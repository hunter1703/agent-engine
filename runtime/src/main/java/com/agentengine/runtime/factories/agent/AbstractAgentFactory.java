package com.agentengine.runtime.factories.agent;

import com.agentengine.runtime.agents.Agent;
import com.agentengine.runtime.factories.agent.builders.BaseLlmAgentBuilder;
import com.agentengine.runtime.factories.model.ModelProvider;
import com.agentengine.runtime.model.AbstractLLM;
import com.agentengine.runtime.tools.ToolFactory;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.BaseTool;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAgentFactory<C extends BaseAgentConfig, A extends Agent> implements AgentFactory<C, A> {
  protected final ModelProvider modelProvider;
  protected final ToolFactory toolFactory;

  protected AbstractAgentFactory(final ModelProvider modelProvider, final ToolFactory toolFactory) {
    this.modelProvider = modelProvider;
    this.toolFactory = toolFactory;
  }

  protected BaseLlmAgentBuilder createLlmAgentBuilder(final BaseAgentConfig config) {
    final BaseLlm model = modelProvider.acquire(config.getModelId());
    if (!(model instanceof AbstractLLM)) {
      throw new IllegalStateException("Model factory did not return an AbstractLLM instance.");
    }

    final LlmAgent.Builder builder = LlmAgent.builder();
    builder.disallowTransferToParent(false).disallowTransferToPeers(false).maxSteps(config.getRuntime().getMaxSteps()).model(model);
    final List<BaseTool> tools = new ArrayList<>(toolFactory.buildTools(config.getTools()));
    if (config.getRuntime() == null || config.getRuntime().isResumable()) {
      tools.add(toolFactory.getHITLTool());
    }
    final BaseLlmAgentBuilder baseLlmAgentBuilder = new BaseLlmAgentBuilder(builder);
    return baseLlmAgentBuilder.systemInstructions(config.getSystemPrompt()).appendTools(tools)
        .appendToolSets(toolFactory.buildToolsets(config.getTools())).agentConfig(config);
  }
}
