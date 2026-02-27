package com.agentengine.engine.agents;

import com.agentengine.engine.agents.flows.DefaultFlow;
import com.agentengine.engine.builders.agent.AgentBuilder;
import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.models.Model;

public class DefaultAgent extends LlmAgent {

  public DefaultAgent(final AgentBuilder builder) {
    super(builder.reWriteInstructions());
  }

  @Override
  protected BaseLlmFlow determineLlmFlow() {
    final AbstractLLM agentModel =
        (AbstractLLM) model().orElse(Model.builder().build()).model().orElse(null);
    if (agentModel == null) {
      return null;
    }
    return new DefaultFlow(agentModel.getParser());
  }
}
