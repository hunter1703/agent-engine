package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.sessionstore.SessionStoreProvider;
import jakarta.inject.Singleton;

@Singleton
public final class PlanningAgentBuilder extends AbstractAgentBuilder<AgentConfig, PlanningAgent> {

  public PlanningAgentBuilder(final ModelProvider modelProvider, SessionStoreProvider sessionStoreProvider) {
    super(modelProvider, sessionStoreProvider);
  }

  @Override
  public PlanningAgent build(final AgentConfig agentConfig) {
    return build(agentConfig, null);
  }

  public PlanningAgent build(final AgentConfig agentConfig, final MessageStore messageStore) {
    final LLMModel planningModel = messageStore == null
        ? modelProvider.get(agentConfig.getName(), agentConfig.getModel())
        : modelProvider.get(agentConfig.getName(), agentConfig.getModel(), messageStore);
    return new PlanningAgent(planningModel);
  }

  @Override
  public String type() {
    return "planning";
  }
}
