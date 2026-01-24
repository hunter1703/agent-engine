package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.*;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.builders.model.ModelProvider;
import jakarta.inject.Singleton;

@Singleton
public final class PlanningAgentBuilder extends AbstractAgentBuilder<AgentConfig, PlanningAgent> {

  public PlanningAgentBuilder(final ModelProvider modelProvider) {
    super(modelProvider);
  }

  @Override
  public PlanningAgent build(final AgentConfig agentConfig) {
    final LLMModel planningModel = modelProvider.get(agentConfig.getName(), agentConfig.getModel());
    return new PlanningAgent(planningModel);
  }

  @Override
  public String type() {
    return "planning";
  }
}
