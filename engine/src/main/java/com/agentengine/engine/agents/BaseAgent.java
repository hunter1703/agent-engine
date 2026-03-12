package com.agentengine.engine.agents;

import com.agentengine.engine.agents.flow.BaseFlow;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;

public final class BaseAgent extends LlmAgent {

  public BaseAgent(final Builder builder) {
    super(builder);
  }

  @Override
  protected BaseLlmFlow determineLlmFlow() {
    return new BaseFlow(maxSteps().orElse(null));
  }
}
