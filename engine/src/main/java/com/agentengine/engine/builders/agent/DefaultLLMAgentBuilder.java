package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.tools.ToolUtils;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import java.util.ArrayList;
import java.util.List;

public class DefaultLLMAgentBuilder extends Agent.Builder<DefaultLLMAgentBuilder, DelegatedAgent> {
  private String protocolInstructions;
  private String systemInstructions;
  private boolean parseToolCallsFromText;
  private final List<BaseTool> tools = new ArrayList<>();
  private final LlmAgent.Builder llmAgentBuilder;

  public DefaultLLMAgentBuilder(LlmAgent.Builder llmAgentBuilder) {
    this.llmAgentBuilder = llmAgentBuilder;
  }

  public DefaultLLMAgentBuilder protocolInstructions(final String protocolInstructions) {

    this.protocolInstructions = protocolInstructions;
    return this;
  }

  public DefaultLLMAgentBuilder systemInstructions(final String systemInstructions) {
    this.systemInstructions = systemInstructions;
    return this;
  }

  public DefaultLLMAgentBuilder parseToolCallsFromText(final boolean parseToolCallsFromText) {
    this.parseToolCallsFromText = parseToolCallsFromText;
    return this;
  }

  public DefaultLLMAgentBuilder appendTools(final List<BaseTool> additionalTools) {
    if (CollectionUtils.isEmpty(additionalTools)) {
      return this;
    }
    tools.addAll(additionalTools);
    return this;
  }

  public List<BaseTool> tools() {
    return List.copyOf(tools);
  }

  public String reWriteInstructions() {
    final String fullToolInstructions =
        parseToolCallsFromText ? "# TOOLS\n" + ToolUtils.buildToolMessage(tools) : "";
    return String.format(
        """
    # YOUR MANDATE:
    %s

    ---

    # PROTOCOL YOU MUST FOLLOW:
    %s

    ---

    %s
    """,
        systemInstructions, protocolInstructions, fullToolInstructions);
  }

  @Override
  public DelegatedAgent build() {
    final String reWrittenInstructions = reWriteInstructions();
    final LlmAgent llmAgent =
        llmAgentBuilder
            .name(name())
            .description(description())
            .subAgents(subAgents())
            .instruction(reWrittenInstructions)
            .tools(tools)
            .build();
    return new DelegatedAgent(llmAgent, agentConfig());
  }
}
