package com.agentengine.engine.builders;

import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.HybridAgent;
import com.agentengine.engine.api.beans.config.EngineConfig;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.HybridAgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.context.LastNContextBuilder;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.api.state.SessionStore;
import com.agentengine.engine.tools.*;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.api.beans.config.LastNContextConfig;
import com.agentengine.commons.utils.CollectionUtils;
import com.agentengine.commons.utils.StringUtils;
import com.agentengine.commons.utils.TemplateUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public final class HybridAgentBuilder extends AbstractAgentBuilder {

  public HybridAgentBuilder(final ConfigRepository configRepository) {
    super(configRepository);
  }

  public HybridAgent build(final String agentName, final AgentConfig agentConfig) {
    final EngineConfig engineConfig = agentConfig.getEngine();
    if (!(engineConfig instanceof HybridAgentConfig hybridConfig)) {
      throw new IllegalArgumentException("Hybrid engine requires hybrid agent config");
    }
    final String promptFromConfig = hybridConfig.getSystemPrompt();
    final String systemPrompt = StringUtils.isBlank(promptFromConfig) ? DEFAULT_SYSTEM_PROMPT : promptFromConfig;

    final String reasoningLLM = hybridConfig.getReasoningModelId();
    final String toolLLM = hybridConfig.getToolAssistantModelId();

    final ModelConfig reasoningConfig = configRepository.loadModelConfig(reasoningLLM);
    final ModelConfig toolAssistantConfig = configRepository.loadModelConfig(toolLLM);

    final LLMModel reasoningModel = buildChatModel(reasoningConfig);
    final LLMModel toolAssistantModel = buildChatModel(toolAssistantConfig);

    final SessionStore sessionStore = buildStateStore(agentConfig.getStateStore());
    final ToolsConfig toolsConfig = agentConfig.getTools();
    final List<Tool> tools = ToolRegistry.loadTools(agentName, toolsConfig);

    final ContextBuilder reasoningContextBuilder = buildReasoningContextBuilder(reasoningConfig, sessionStore,
        systemPrompt, tools);

    final Map<String, Tool> toolMap = new HashMap<>();
    for (Tool tool : CollectionUtils.nullSafeList(tools)) {
      toolMap.put(tool.name(), tool);
    }
    final Map<String, Tool> toolByName = Collections.unmodifiableMap(toolMap);

    final ContextBuilder toolAssistantContextBuilder = buildToolAssistantContextBuilder(toolAssistantConfig,
        sessionStore, tools);

    final AgenticToolExecutor toolExecutor = new ToolRequestExecutor(toolAssistantModel, toolAssistantContextBuilder,
        toolByName, sessionStore);

    return new HybridAgent(reasoningModel, toolExecutor, reasoningContextBuilder, sessionStore, 25);
  }

  private ContextBuilder buildToolAssistantContextBuilder(final ModelConfig config, final SessionStore sessionStore,
      final List<Tool> tools) {
    String systemPrompt = "You are a helpful tool assistant.";
    String protocolMessage = ""; // Protocol now handled just-in-time in ToolRequestExecutor for dynamic parts

    return switch (config.getContextConfig()) {
      case LastNContextConfig lastNContextConfig ->
        new LastNContextBuilder(sessionStore, systemPrompt, protocolMessage,
            tools, lastNContextConfig.getKeepLast());
      default -> throw new IllegalArgumentException("Unknown context config for tool assistant");
    };
  }

  @Override
  public String type() {
    return "hybrid";
  }

  private ContextBuilder buildReasoningContextBuilder(final ModelConfig config, final SessionStore sessionStore,
      final String systemMessage, final List<Tool> tools) {
    final String templateName = resolveReasoningProtocolTemplate(config.getResponseFormat());
    final Map<String, Object> context = new HashMap<>();
    context.put("thoughtsEnabled", config.isThoughtsEnabled());
    if ("json".equalsIgnoreCase(config.getResponseFormat())) {
      context.put("response_schema", loadReasonerSchema(config));
    }
    final String protocolMessage = TemplateUtils.renderTemplateForName(templateName, context);
    return switch (config.getContextConfig()) {
      case LastNContextConfig lastNContextConfig ->
        new LastNContextBuilder(sessionStore, systemMessage, protocolMessage,
            tools, lastNContextConfig.getKeepLast());
      default -> throw new IllegalArgumentException("Unknown context config");
    };
  }

  private static String resolveReasoningProtocolTemplate(final String responseFormat) {
    if ("json".equalsIgnoreCase(responseFormat)) {
      return "hybrid/reasoner_json.txt";
    } else {
      return "hybrid/reasoner_text.txt";
    }
  }

  private static String loadReasonerSchema(final ModelConfig config) {
    if (!"json".equalsIgnoreCase(config.getResponseFormat())) {
      return "";
    }
    String path = config.isThoughtsEnabled() ? "/schemas/hybrid/reasoner_with_thoughts.json"
        : "/schemas/hybrid/reasoner_without_thoughts.json";
    return com.agentengine.commons.utils.ResourceUtils.loadResourceAsString(path);
  }

  private static String loadToolCallSchema() {
    return com.agentengine.commons.utils.ResourceUtils.loadResourceAsString("/schemas/hybrid/tool_call_schema.json");
  }
}
