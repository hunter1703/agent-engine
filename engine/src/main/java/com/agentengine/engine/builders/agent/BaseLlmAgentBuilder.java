package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.agents.BaseAgent;
import com.agentengine.engine.tools.HumanInTheLoopTool;
import com.agentengine.engine.api.Agent;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import java.util.ArrayList;
import java.util.List;

public class BaseLlmAgentBuilder extends Agent.Builder<BaseLlmAgentBuilder, DelegatedAgent> {
  private String systemInstructions;
  private final List<BaseTool> tools = new ArrayList<>();
  private final List<BaseToolset> toolSets = new ArrayList<>();
  private final LlmAgent.Builder llmAgentBuilder;

  public BaseLlmAgentBuilder(final LlmAgent.Builder llmAgentBuilder) {
    this.llmAgentBuilder = llmAgentBuilder;
  }

  public BaseLlmAgentBuilder systemInstructions(final String systemInstructions) {
    this.systemInstructions = systemInstructions;
    return this;
  }

  public BaseLlmAgentBuilder disallowTransferToParent(final boolean disallowTransferToParent) {
    llmAgentBuilder.disallowTransferToParent(disallowTransferToParent);
    return this;
  }

  public BaseLlmAgentBuilder disallowTransferToPeers(final boolean disallowTransferToPeers) {
    llmAgentBuilder.disallowTransferToPeers(disallowTransferToPeers);
    return this;
  }

  public BaseLlmAgentBuilder appendTools(final List<BaseTool> additionalTools) {
    if (CollectionUtils.isEmpty(additionalTools)) {
      return this;
    }
    tools.addAll(additionalTools);
    return this;
  }

  public BaseLlmAgentBuilder appendToolSets(final List<BaseToolset> additionalToolSets) {
    if (CollectionUtils.isEmpty(additionalToolSets)) {
      return this;
    }
    toolSets.addAll(additionalToolSets);
    return this;
  }

  @Override
  public DelegatedAgent build() {
    final List<Object> toolsAndToolsets = new ArrayList<>();
    toolsAndToolsets.add(new HumanInTheLoopTool());
    toolsAndToolsets.addAll(tools);
    toolsAndToolsets.addAll(toolSets);
    final LlmAgent.Builder builder =
        llmAgentBuilder
            .name(name())
            .description(description())
            .subAgents(subAgents())
            .instruction(systemInstructions)
            .tools(toolsAndToolsets);
    final LlmAgent llmAgent = new BaseAgent(builder);
    return new DelegatedAgent(llmAgent, agentConfig());
  }
}
