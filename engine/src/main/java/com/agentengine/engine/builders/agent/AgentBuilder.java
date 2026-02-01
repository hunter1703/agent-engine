package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.SimpleAgent;
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
    return super.instruction(STR."# GLOBAL INSTRUCTION\n\{globalInstruction}\n\n# PROTOCOL YOU MUST FOLLOW\n\{protocolInstructions}\n\n---\n\n# TOOLS\n\{toolInstructions}");
  }

  @Override
  public SimpleAgent build() {
    validate();
    return new SimpleAgent(this) {
    };
  }
}
