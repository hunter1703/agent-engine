package com.agentengine.agent.infra.factories.agent;

import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.factories.agent.builders.BaseLlmAgentBuilder;
import com.agentengine.agent.infra.factories.context.CompactorProvider;
import com.agentengine.agent.infra.tools.ToolFactory;
import com.agentengine.agent.infra.utils.PromptUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.ToolsConfig;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.models.ConfiguredModel;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import com.google.genai.types.GenerateContentConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractAgentFactory<C extends BaseAgentConfig, A extends Agent>
    implements AgentFactory<C, A> {
  protected final ModelProvider modelProvider;
  protected final ToolFactory toolFactory;
  protected final CompactorProvider compactorProvider;

  protected AbstractAgentFactory(
      final ModelProvider modelProvider,
      final ToolFactory toolFactory,
      final CompactorProvider compactorProvider) {
    this.modelProvider = modelProvider;
    this.toolFactory = toolFactory;
    this.compactorProvider = compactorProvider;
  }

  protected BaseLlmAgentBuilder createLlmAgentBuilder(final BaseAgentConfig config) {
    final String modelId = config.getModelId();
    final RefCounted<ConfiguredModel<BaseLlm>> refCounted = modelProvider.get(modelId);
    final BaseLlm model = refCounted.value().model();
    final LlmAgent.Builder builder = LlmAgent.builder();
    builder
        .disallowTransferToParent(false)
        .disallowTransferToPeers(false)
        .maxSteps(config.getRuntime().getMaxSteps())
        .model(model);
    final Map<String, Object> responseFormat = config.getResponseFormat();
    if (CollectionUtils.isNotEmpty(responseFormat)) {
      builder.generateContentConfig(
          GenerateContentConfig.builder()
              .responseMimeType("application/json")
              .responseJsonSchema(responseFormat)
              .build());
    }
    final boolean toolCallingEnabled = modelProvider.isToolCallingEnabled(modelId);
    final List<BaseTool> tools;
    final List<BaseToolset> toolsets;
    final boolean hasTools;
    final boolean isResumable = toolCallingEnabled && config.getRuntime().isResumable();
    if (toolCallingEnabled) {
      final Set<ToolsConfig> toolsConfigs = CollectionUtils.nullSafeMutableSet(config.getTools());
      if (isResumable) {
        toolsConfigs.add(new ToolsConfig(Constants.ToolNames.HITL, Map.of()));
      }
      toolsConfigs.add(new ToolsConfig(Constants.ToolNames.SEARCH_KNOWLEDGE, Map.of()));
      toolsConfigs.add(new ToolsConfig(Constants.ToolNames.REFRESH_REMINDERS, Map.of()));
      toolsConfigs.add(new ToolsConfig(Constants.Toolsets.NOTEBOOK, Map.of()));
      tools = toolFactory.buildTools(toolsConfigs);
      toolsets = toolFactory.buildToolsets(toolsConfigs);
      hasTools = CollectionUtils.isNotEmpty(tools) || CollectionUtils.isNotEmpty(toolsets);
    } else {
      tools = List.of();
      toolsets = List.of();
      hasTools = false;
    }

    final BaseLlmAgentBuilder baseLlmAgentBuilder = new BaseLlmAgentBuilder(builder);
    return baseLlmAgentBuilder
        .systemInstructions(
            PromptUtils.renderSystemPrompt(
                config.getSystemPrompt(),
                config.getName(),
                config.getResponseFormat(),
                hasTools,
                isResumable))
        .appendTools(tools)
        .appendToolSets(toolsets)
        .compactor(compactorProvider.create(config))
        .agentConfig(config)
        .closeHook(refCounted::close);
  }
}
