package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.sessionstore.SessionStoreProvider;
import com.agentengine.engine.tools.ToolRegistry;
import jakarta.inject.Singleton;

@Singleton
public final class PlanningAgentBuilder extends AbstractAgentBuilder<AgentConfig, PlanningAgent> {

  public PlanningAgentBuilder(final ModelProvider modelProvider, SessionStoreProvider sessionStoreProvider,
      ToolRegistry toolRegistry) {
    super(modelProvider, sessionStoreProvider, toolRegistry);
  }

  @Override
  public PlanningAgent build(final AgentConfig agentConfig) {
    return build(agentConfig, null);
  }

  public PlanningAgent build(final AgentConfig agentConfig, final MessageStore messageStore) {
    final LLMModel planningModel = messageStore == null
        ? modelProvider.get(agentConfig.getAgentId(), agentConfig.getModel())
        : modelProvider.get(agentConfig.getAgentId(), agentConfig.getModel(), messageStore);
    return new PlanningAgent(planningModel);
  }

  @Override
  public String type() {
    return "planning";
  }
}
