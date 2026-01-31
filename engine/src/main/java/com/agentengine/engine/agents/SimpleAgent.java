package com.agentengine.engine.agents;

import com.agentengine.engine.agents.flows.SimpleFlow;
import com.agentengine.engine.builders.agent.AgentBuilder;
import com.agentengine.engine.model.LangChain4JLLMModel;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.models.Model;

public class SimpleAgent extends LlmAgent {

  public SimpleAgent(final AgentBuilder builder) {
    super(builder.reWriteInstructions());
  }

  @Override
  protected BaseLlmFlow determineLlmFlow() {
    LangChain4JLLMModel model = (LangChain4JLLMModel) model().orElse(Model.builder().build()).model().orElse(null);
    if (model == null) {
      return null;
    }
    return new SimpleFlow(maxSteps().orElse(10), model.getRequestProcessors(), model.getResponseProcessors());
  }

}
