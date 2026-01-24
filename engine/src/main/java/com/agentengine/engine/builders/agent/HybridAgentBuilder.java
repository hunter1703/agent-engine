package com.agentengine.engine.builders.agent;

import com.agentengine.engine.HybridAgent;
import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.agents.ToolAssistantAgent;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.state.InMemoryStateStore;
import com.agentengine.engine.tools.DefaultToolExecutor;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.ToolExecutor;
import com.agentengine.engine.tools.ToolRegistry;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public final class HybridAgentBuilder extends AbstractAgentBuilder<HybridAgentConfig, HybridAgent> {
  private static final int DEFAULT_INVOCATION_LIMIT = 6;

  private final PlanningAgentBuilder planningAgentBuilder;
  private final ToolRegistry toolRegistry;

  public HybridAgentBuilder(final ModelProvider modelProvider, final PlanningAgentBuilder planningAgentBuilder,
                            final ToolRegistry toolRegistry) {
    super(modelProvider);
    this.planningAgentBuilder = planningAgentBuilder;
    this.toolRegistry = toolRegistry;
  }

  @Override
  public HybridAgent build(final HybridAgentConfig agentConfig) {
    final LLMModel reasoningModel = modelProvider.get(agentConfig.getName(), agentConfig.getModel());
    final LLMModel routerModel = modelProvider.get(agentConfig.getName(), agentConfig.getRouterModel());
    final List<Tool> tools = toolRegistry.loadTools(agentConfig.getName(), agentConfig.getModel().getTools());
    final ToolExecutor toolExecutor = new DefaultToolExecutor(
        toolRegistry.loadToolHandlers(agentConfig.getName(), agentConfig.getModel().getTools()));

    final PlanningAgent planningAgent = createPlanningAgent(agentConfig);
    final ToolAssistantAgent toolAssistantAgent = new ToolAssistantAgent(reasoningModel, tools, new InMemoryStateStore());

    return new HybridAgent(new HybridAgent.Dependencies(routerModel, planningAgent, reasoningModel, toolAssistantAgent,
        toolExecutor, reasoningModel.getContextManager(), DEFAULT_INVOCATION_LIMIT));
  }

  @Override
  public String type() {
    return "hybrid";
  }

  private PlanningAgent createPlanningAgent(final HybridAgentConfig agentConfig) {
    final AgentConfig config = new AgentConfig();
    config.setName(agentConfig.getName());
    config.setModel(agentConfig.getPlanningModel());
    return planningAgentBuilder.build(config);
  }
}
