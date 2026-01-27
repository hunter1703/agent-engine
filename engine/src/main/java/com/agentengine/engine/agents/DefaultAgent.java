package com.agentengine.engine.agents;

import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.tools.ToolExecutor;

public final class DefaultAgent extends AbstractSingleModelAgent {

  public DefaultAgent(final String name, final String description, final LLMModel model) {
    super(name, description, model);
  }

  public DefaultAgent(final String name, final String description, final LLMModel model,
      final ToolExecutor toolExecutor) {
    super(name, description, model, toolExecutor);
  }
}