package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DefaultAgent;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.agents.LlmAgent;

public class AgentBuilder extends LlmAgent.Builder {
  private String protocolInstructions;
  private String toolInstructions;
  private String globalInstruction;

  public AgentBuilder protocolInstructions(final String protocolInstructions) {
    this.protocolInstructions = protocolInstructions;
    return this;
  }

  public AgentBuilder toolInstructions(final String toolInstructions) {
    this.toolInstructions = toolInstructions;
    return this;
  }

  public AgentBuilder globalInstruction(final String globalInstruction) {
    this.globalInstruction = globalInstruction;
    return this;
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
