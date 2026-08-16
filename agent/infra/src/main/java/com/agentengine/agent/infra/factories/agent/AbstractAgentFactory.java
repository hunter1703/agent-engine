package com.agentengine.agent.infra.factories.agent;

import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.factories.agent.builders.BaseLlmAgentBuilder;
import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.agent.infra.model.AbstractLLM;
import com.agentengine.agent.infra.tools.ToolFactory;
import com.agentengine.agent.infra.tools.knowledge.SearchKnowledgeTool;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.PromptUtils;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.LoadArtifactsTool;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAgentFactory<C extends BaseAgentConfig, A extends Agent>
    implements AgentFactory<C, A> {
  protected final ModelProvider modelProvider;
  protected final ToolFactory toolFactory;

  protected AbstractAgentFactory(final ModelProvider modelProvider, final ToolFactory toolFactory) {
    this.modelProvider = modelProvider;
    this.toolFactory = toolFactory;
  }

  protected BaseLlmAgentBuilder createLlmAgentBuilder(final BaseAgentConfig config) {
    final String modelId = config.getModelId();
    final BaseLlm model = modelProvider.acquire(modelId);
    if (!(model instanceof AbstractLLM)) {
      modelProvider.release(modelId);
      throw new IllegalStateException("Model factory did not return an AbstractLLM instance.");
    }

    final LlmAgent.Builder builder = LlmAgent.builder();
    builder
        .disallowTransferToParent(false)
        .disallowTransferToPeers(false)
        .maxSteps(config.getRuntime().getMaxSteps())
        .model(model);
    final List<BaseTool> tools = new ArrayList<>(toolFactory.buildTools(config.getTools()));
    List<String> standardTools = CollectionUtils.nullSafeList(config.getStandardTools());
    if (standardTools.contains(LoadArtifactsTool.INSTANCE.name())) {
      tools.add(LoadArtifactsTool.INSTANCE);
    }
    if (standardTools.contains(SearchKnowledgeTool.DESCRIPTOR.name())) {
      tools.add(toolFactory.getSearchKnowledgeTool());
    }

    if (config.getRuntime() == null || config.getRuntime().isResumable()) {
      tools.add(toolFactory.getHITLTool());
    }
    final BaseLlmAgentBuilder baseLlmAgentBuilder = new BaseLlmAgentBuilder(builder);
    return baseLlmAgentBuilder
        .systemInstructions(
            PromptUtils.renderSystemPrompt(
                config.getSystemPrompt(), config.getName(), config.getResponseFormat()))
        .appendTools(tools)
        .appendToolSets(toolFactory.buildToolsets(config.getTools()))
        .agentConfig(config)
        .closeHook(() -> modelProvider.release(modelId));
  }
}
