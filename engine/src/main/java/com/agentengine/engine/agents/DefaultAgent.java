package com.agentengine.engine.agents;

import com.agentengine.engine.agents.flows.DefaultFlow;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.builders.agent.LLMAgentBuilder;
import com.agentengine.engine.guardrails.Guardrail;
import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.models.Model;
import java.util.List;

public class DefaultAgent extends LlmAgent {
  private final List<Guardrail> outputGuardrails;
  private final GuardrailErrorMode guardrailErrorMode;

  public DefaultAgent(final LLMAgentBuilder builder) {
    super(builder.reWriteInstructions());
    this.outputGuardrails = builder.outputGuardrails();
    this.guardrailErrorMode = builder.guardrailErrorMode();
  }

  @Override
  protected BaseLlmFlow determineLlmFlow() {
    final AbstractLLM agentModel =
        (AbstractLLM) model().orElse(Model.builder().build()).model().orElse(null);
    if (agentModel == null) {
      return null;
    }
    return new DefaultFlow(agentModel.getParser(), outputGuardrails, guardrailErrorMode);
  }
}
