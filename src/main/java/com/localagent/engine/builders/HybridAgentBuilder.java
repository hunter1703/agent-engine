package com.localagent.engine.builders;

import com.localagent.engine.HybridEngine;
import com.localagent.engine.beans.config.AgentConfig;
import com.localagent.engine.beans.config.EngineConfig;
import com.localagent.engine.beans.config.ModelConfig;
import com.localagent.engine.beans.config.ToolsConfig;
import com.localagent.engine.context.ContextBuilder;
import com.localagent.engine.model.LLMModel;
import com.localagent.engine.state.SessionStore;
import com.localagent.engine.tools.AgentTool;
import com.localagent.engine.tools.ToolRegistry;
import com.localagent.engine.utils.CollectionUtils;
import com.localagent.engine.utils.ResourceUtils;
import com.localagent.engine.utils.StringUtils;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public final class HybridAgentBuilder extends AbstractAgentBuilder {

    public HybridEngine build(String agentName, AgentConfig agentConfig) {
        final EngineConfig engineConfig = agentConfig.getEngine();
        final String promptFromConfig = engineConfig.getPrompt();
        final String systemPrompt = StringUtils.isBlank(promptFromConfig) ? DEFAULT_SYSTEM_PROMPT : promptFromConfig;

        final String reasoningLLM = engineConfig.getReasoning();
        final String toolLLM = engineConfig.getTool();

        final ModelConfig reasoningConfig = Objects.requireNonNull(ResourceUtils.loadModelConfig(reasoningLLM));
        final ModelConfig toolAssistantConfig = Objects.requireNonNull(ResourceUtils.loadModelConfig(toolLLM));

        final LLMModel reasoningModel = buildChatModel(reasoningConfig);
        final LLMModel toolAssistantModel = buildChatModel(toolAssistantConfig);

        final SessionStore sessionStore = buildStateStore(agentConfig.getStateStore());
        ToolsConfig toolsConfig = agentConfig.getTools();
        List<String> enabledTools = CollectionUtils.nullSafeList(toolsConfig == null ? List.of("ALL") : toolsConfig.getEnabled());
        Map<String, Map<String, Object>> toolConfigs = CollectionUtils.nullSafeMap(toolsConfig == null ? Map.of() : toolsConfig.getConfigs());
        List<AgentTool> tools = ToolRegistry.loadTools(agentName, enabledTools, toolConfigs, agentConfig);

        final ContextBuilder reasoningContextBuilder = buildReasoningContextBuilder(reasoningConfig, sessionStore, true, systemPrompt, tools);
        final ContextBuilder toolAssistantContextBuilder = buildToolAssistantContextBuilder(toolAssistantConfig, sessionStore, systemPrompt, tools);
        return new HybridEngine(reasoningModel, toolAssistantModel, tools, reasoningContextBuilder, toolAssistantContextBuilder, sessionStore, 25);
    }

    @Override
    public List<String> agentNames() {
        return null;
    }
}
