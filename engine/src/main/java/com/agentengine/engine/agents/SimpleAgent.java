package com.agentengine.engine.agents;

import com.agentengine.engine.agents.flows.SimpleFlow;
import com.agentengine.engine.builders.agent.AgentBuilder;
import com.agentengine.engine.model.LangChain4JLLMModel;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;

public class SimpleAgent extends LlmAgent {
  private final LangChain4JLLMModel model;

  public SimpleAgent(final AgentBuilder builder) {
    super(builder.reWriteInstructions());
    this.model = builder.getModel();
  }

  @Override
  protected BaseLlmFlow determineLlmFlow() {
    return new SimpleFlow(maxSteps().orElse(10), model.getRequestProcessors(), model.getResponseProcessors());
  }

}
