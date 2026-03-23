package com.agentengine.runtime.factories.agent;

import com.agentengine.runtime.api.beans.config.BaseAgentConfig;
import com.agentengine.runtime.builders.agent.BaseLlmAgentBuilder;
import com.agentengine.runtime.factories.model.ModelProvider;
import com.agentengine.runtime.model.AbstractLLM;
import com.agentengine.runtime.plugin.Agent;
import com.agentengine.runtime.plugin.factories.AgentFactory;
import com.agentengine.runtime.tools.ToolFactory;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;

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
    final BaseLlmAgentBuilder baseLlmAgentBuilder = new BaseLlmAgentBuilder(builder);
    return baseLlmAgentBuilder.includeHumanInTheLoopTool(config.getRuntime() == null || config.getRuntime().isResumable())
        .systemInstructions(config.getSystemPrompt()).appendTools(toolFactory.buildTools(config.getTools()))
        .appendToolSets(toolFactory.buildToolsets(config.getTools())).agentConfig(config);
  }
}
