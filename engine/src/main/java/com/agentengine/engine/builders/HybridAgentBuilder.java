package com.agentengine.engine.builders;

import com.agentengine.engine.client.ConfigRepository;
import com.agentengine.engine.HybridEngine;
import com.agentengine.engine.client.beans.config.EngineConfig;
import com.agentengine.engine.client.beans.config.AgentConfig;
import com.agentengine.engine.client.beans.config.HybridEngineConfig;
import com.agentengine.engine.client.beans.config.ModelConfig;
import com.agentengine.engine.client.beans.config.ToolsConfig;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.client.state.SessionStore;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.commons.utils.StringUtils;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public final class HybridAgentBuilder extends AbstractAgentBuilder {

  public HybridAgentBuilder(final ConfigRepository configRepository) {
    super(configRepository);
  }

  public HybridEngine build(final String agentName, final AgentConfig agentConfig) {
    final EngineConfig engineConfig = agentConfig.getEngine();
    if (!(engineConfig instanceof HybridEngineConfig hybridConfig)) {
      throw new IllegalArgumentException("Hybrid engine requires hybrid engine config");
    }
    final String promptFromConfig = hybridConfig.getSystemPrompt();
    final String systemPrompt = StringUtils.isBlank(promptFromConfig) ? DEFAULT_SYSTEM_PROMPT : promptFromConfig;

    final String reasoningLLM = hybridConfig.getReasoning();
    final String toolLLM = hybridConfig.getTool();

    final ModelConfig reasoningConfig = configRepository.loadModelConfig(reasoningLLM);
    final ModelConfig toolAssistantConfig = configRepository.loadModelConfig(toolLLM);

    final LLMModel reasoningModel = buildChatModel(reasoningConfig);
    final LLMModel toolAssistantModel = buildChatModel(toolAssistantConfig);

    final SessionStore sessionStore = buildStateStore(agentConfig.getStateStore());
    final ToolsConfig toolsConfig = agentConfig.getTools();
    final List<Tool> tools = ToolRegistry.loadTools(agentName, toolsConfig);

    final ContextBuilder reasoningContextBuilder = buildReasoningContextBuilder(reasoningConfig, sessionStore, true,
        systemPrompt, tools);
    final ContextBuilder toolAssistantContextBuilder = buildToolAssistantContextBuilder(toolAssistantConfig,
        sessionStore, systemPrompt, tools);
    return new HybridEngine(reasoningModel, toolAssistantModel, tools, reasoningContextBuilder,
        toolAssistantContextBuilder, sessionStore, 25);
  }

  @Override
  public String type() {
    return null;
  }
}
