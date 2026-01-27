package com.agentengine.engine.builders.agent;

import com.agentengine.engine.HybridAgent;
import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.SessionStore;
import com.agentengine.engine.builders.messagestore.MessageStoreProvider;
import com.agentengine.engine.builders.sessionstore.SessionStoreProvider;
import com.agentengine.engine.tools.*;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public final class HybridAgentBuilder extends AbstractAgentBuilder<HybridAgentConfig, HybridAgent> {
  private static final int DEFAULT_INVOCATION_LIMIT = 6;

  private final PlanningAgentBuilder planningAgentBuilder;
  private final MessageStoreProvider messageStoreProvider;

  public HybridAgentBuilder(final ModelProvider modelProvider, final PlanningAgentBuilder planningAgentBuilder,
      final ToolRegistry toolRegistry, final SessionStoreProvider sessionStoreProvider,
      final MessageStoreProvider messageStoreProvider) {
    super(modelProvider, sessionStoreProvider, toolRegistry);
    this.planningAgentBuilder = planningAgentBuilder;
    this.messageStoreProvider = messageStoreProvider;
  }

  @Override
  public HybridAgent build(final HybridAgentConfig agentConfig) {
    final MessageStore sharedMessageStore = messageStoreProvider
        .get(agentConfig.getModel().getContextManagerConfig().getMessageStore());
    final LLMModel reasoningModel = modelProvider.get(agentConfig.getAgentId(), agentConfig.getModel(),
        sharedMessageStore);
    final LLMModel routerModel = modelProvider.get(agentConfig.getAgentId(), agentConfig.getRouterModel(),
        sharedMessageStore);

    final ToolsConfig toolsConfig = agentConfig.getModel().getTools();
    final List<Tool> tools = toolRegistry.loadTools(agentConfig.getAgentId(), toolsConfig);
    final PlanningAgent planningAgent = createPlanningAgent(agentConfig, sharedMessageStore);
    tools.add(new UserClarificationTool());
    tools.add(planningAgent);

    final ToolExecutor toolExecutor = new DefaultToolExecutor(tools);

    final SessionStore sessionStore = sessionStoreProvider.get(agentConfig.getSessionStore());
    return new HybridAgent(reasoningModel, routerModel, toolExecutor, sessionStore, DEFAULT_INVOCATION_LIMIT,
        agentConfig.getAgentId());
  }

  @Override
  public String type() {
    return "hybrid";
  }

  private PlanningAgent createPlanningAgent(final HybridAgentConfig agentConfig, final MessageStore messageStore) {
    final AgentConfig config = new AgentConfig();
    config.setAgentId(agentConfig.getAgentId());
    config.setModel(agentConfig.getPlanningModel());
    return planningAgentBuilder.build(config, messageStore);
  }
}
