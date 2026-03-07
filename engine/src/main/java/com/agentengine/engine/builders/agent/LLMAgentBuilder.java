package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DefaultAgent;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.beans.config.OutputEvaluationConfig;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.guardrails.Guardrail;
import com.google.adk.agents.LlmAgent;
import java.util.List;

public class LLMAgentBuilder extends LlmAgent.Builder {
  private String protocolInstructions;
  private String toolInstructions;
  private String globalInstruction;
  private List<Guardrail> outputGuardrails = List.of();
  private GuardrailErrorMode guardrailErrorMode = GuardrailErrorMode.FAIL_OPEN;
  private OutputEvaluationConfig outputEvaluationConfig = new OutputEvaluationConfig();

  public LLMAgentBuilder() {
    this.disallowTransferToParent(false);
    this.disallowTransferToPeers(false);
  }

  public LLMAgentBuilder protocolInstructions(final String protocolInstructions) {

    this.protocolInstructions = protocolInstructions;
    return this;
  }

  public LLMAgentBuilder toolInstructions(final String toolInstructions) {
    this.toolInstructions = toolInstructions;
    return this;
  }

  public LLMAgentBuilder globalInstruction(final String globalInstruction) {
    this.globalInstruction = globalInstruction;
    return this;
  }

  public LLMAgentBuilder outputGuardrails(final List<Guardrail> outputGuardrails) {
    this.outputGuardrails = outputGuardrails == null ? List.of() : List.copyOf(outputGuardrails);
    return this;
  }

  public LLMAgentBuilder guardrailErrorMode(final GuardrailErrorMode guardrailErrorMode) {
    this.guardrailErrorMode =
        guardrailErrorMode == null ? GuardrailErrorMode.FAIL_OPEN : guardrailErrorMode;
    return this;
  }

  public LLMAgentBuilder outputEvaluationConfig(final OutputEvaluationConfig outputEvaluationConfig) {
    this.outputEvaluationConfig =
        outputEvaluationConfig == null ? new OutputEvaluationConfig() : outputEvaluationConfig;
    return this;
  }

  public List<Guardrail> outputGuardrails() {
    return outputGuardrails;
  }

  public GuardrailErrorMode guardrailErrorMode() {
    return guardrailErrorMode;
  }

  public OutputEvaluationConfig outputEvaluationConfig() {
    return outputEvaluationConfig;
  }

  public LlmAgent.Builder reWriteInstructions() {
    final String fullToolInstructions =
        StringUtils.isNotBlank(toolInstructions) ? "# TOOLS\n" + toolInstructions : "";
    this.instruction(String.format("""
    # YOUR MANDATE:
    %s

    ---

    # PROTOCOL YOU MUST FOLLOW:
    %s

    ---

    %s
    """, globalInstruction, protocolInstructions, fullToolInstructions));
    return this;
  }

  @Override
  public DefaultAgent build() {
    validate();
    return new DefaultAgent(this) {};
  }
}
