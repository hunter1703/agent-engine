package com.agentengine.engine.agents;

import com.agentengine.engine.api.LLMModel;

public final class DefaultAgent extends AbstractSingleModelAgent {

  public DefaultAgent(final String name, final String description, final LLMModel model) {
    super(name, description, model);
  }
}