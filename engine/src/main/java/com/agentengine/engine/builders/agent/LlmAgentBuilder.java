package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LlmAgentBuilder extends Agent.Builder<LlmAgentBuilder, DelegatedAgent> {
  private String systemInstructions;
  private final List<BaseTool> tools = new ArrayList<>();
  private final List<BaseToolset> toolSets = new ArrayList<>();
  private final LlmAgent.Builder llmAgentBuilder;

  public LlmAgentBuilder(final LlmAgent.Builder llmAgentBuilder) {
    this.llmAgentBuilder = llmAgentBuilder;
  }

  public LlmAgentBuilder systemInstructions(final String systemInstructions) {
    this.systemInstructions = systemInstructions;
    return this;
  }

  public LlmAgentBuilder disallowTransferToParent(final boolean disallowTransferToParent) {
    llmAgentBuilder.disallowTransferToParent(disallowTransferToParent);
    return this;
  }

  public LlmAgentBuilder disallowTransferToPeers(final boolean disallowTransferToPeers) {
    llmAgentBuilder.disallowTransferToPeers(disallowTransferToPeers);
    return this;
  }

  public LlmAgentBuilder appendTools(final List<BaseTool> additionalTools) {
    if (CollectionUtils.isEmpty(additionalTools)) {
      return this;
    }
    tools.addAll(additionalTools);
    return this;
  }

  public LlmAgentBuilder appendToolSets(final List<BaseToolset> additionalToolSets) {
    if (CollectionUtils.isEmpty(additionalToolSets)) {
      return this;
    }
    toolSets.addAll(additionalToolSets);
    return this;
  }

  @Override
  public DelegatedAgent build() {
    final List<Object> toolsAndToolsets = new ArrayList<>(tools);
    toolsAndToolsets.addAll(toolSets);
    final LlmAgent llmAgent =
        llmAgentBuilder
            .name(name())
            .description(description())
            .subAgents(subAgents())
            .instruction(systemInstructions)
            .tools(toolsAndToolsets)
            .build();
    return new DelegatedAgent(llmAgent, agentConfig());
  }
}
