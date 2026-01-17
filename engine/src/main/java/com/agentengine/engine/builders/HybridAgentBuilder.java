package com.agentengine.engine.builders;

import com.agentengine.engine.HybridEngine;
import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.EngineConfig;
import com.agentengine.engine.beans.config.ModelConfig;
import com.agentengine.engine.beans.config.ToolsConfig;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.state.SessionStore;
import com.agentengine.engine.tools.AgentTool;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.utils.CollectionUtils;
import com.agentengine.engine.utils.ResourceUtils;
import com.agentengine.engine.utils.StringUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public final class HybridAgentBuilder extends AbstractAgentBuilder {

  public HybridEngine build(final String agentName, final AgentConfig agentConfig) {
    final EngineConfig engineConfig = agentConfig.getEngine();
    final String promptFromConfig = engineConfig.getPrompt();
    final String systemPrompt =
        StringUtils.isBlank(promptFromConfig) ? DEFAULT_SYSTEM_PROMPT : promptFromConfig;

    final String reasoningLLM = engineConfig.getReasoning();
    final String toolLLM = engineConfig.getTool();

    final ModelConfig reasoningConfig =
        Objects.requireNonNull(ResourceUtils.loadModelConfig(reasoningLLM));
    final ModelConfig toolAssistantConfig =
        Objects.requireNonNull(ResourceUtils.loadModelConfig(toolLLM));

    final LLMModel reasoningModel = buildChatModel(reasoningConfig);
    final LLMModel toolAssistantModel = buildChatModel(toolAssistantConfig);

    final SessionStore sessionStore = buildStateStore(agentConfig.getStateStore());
    final ToolsConfig toolsConfig = agentConfig.getTools();
    final List<String> enabledTools =
        CollectionUtils.nullSafeList(
            toolsConfig == null ? List.of("ALL") : toolsConfig.getEnabled());
    final Map<String, Map<String, Object>> toolConfigs =
        CollectionUtils.nullSafeMap(toolsConfig == null ? Map.of() : toolsConfig.getConfigs());
    final List<AgentTool> tools =
        ToolRegistry.loadTools(agentName, enabledTools, toolConfigs, agentConfig);

    final ContextBuilder reasoningContextBuilder =
        buildReasoningContextBuilder(reasoningConfig, sessionStore, true, systemPrompt, tools);
    final ContextBuilder toolAssistantContextBuilder =
        buildToolAssistantContextBuilder(toolAssistantConfig, sessionStore, systemPrompt, tools);
    return new HybridEngine(
        reasoningModel,
        toolAssistantModel,
        tools,
        reasoningContextBuilder,
        toolAssistantContextBuilder,
        sessionStore,
        25);
  }

  @Override
  public List<String> agentNames() {
    return null;
  }
}
