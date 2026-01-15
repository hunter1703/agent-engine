package com.localagent.engine.builders;

import com.localagent.engine.DefaultAgentEngine;
import com.localagent.engine.config.*;
import com.localagent.engine.context.ContextBuilder;
import com.localagent.engine.context.LastNContextBuilder;
import com.localagent.engine.context.SummarizingContextBuilder;
import com.localagent.engine.message.Message;
import com.localagent.engine.model.LLMModel;
import com.localagent.engine.model.ModelFactory;
import com.localagent.engine.prompts.PromptTemplates;
import com.localagent.engine.state.SessionStore;
import com.localagent.engine.state.InMemorySessionStore;
import com.localagent.engine.tools.AgentTool;
import com.localagent.engine.tools.ToolRegistry;
import com.localagent.engine.utils.ResourceUtils;
import com.localagent.engine.utils.StringUtils;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Singleton
public final class DefaultAgentBuilder implements AgentBuilder {
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";
    private static final Logger LOGGER = Logger.getLogger(DefaultAgentBuilder.class.getName());

    public DefaultAgentEngine build(String agentName, AgentConfig agentConfig) {
        EngineConfig engineConfig = agentConfig.getEngine();
        final String promptFromConfig = engineConfig.getPrompt();
        String systemPrompt = StringUtils.isBlank(promptFromConfig) ? DEFAULT_SYSTEM_PROMPT : promptFromConfig;

        Message systemMessage = Message.system(systemPrompt);

        String reasoningLLM = engineConfig.getReasoning();
        String toolLLM = engineConfig.getTool();
        String routerLLM = engineConfig.getRouter();
        String heavyLLM = engineConfig.getHeavy();

        final ModelConfig reasoningSettings = ResourceUtils.loadModelConfig(reasoningLLM);
        final ModelConfig toolSettings = ResourceUtils.loadModelConfig(toolLLM);
        final ModelConfig heavySettings = ResourceUtils.loadModelConfig(heavyLLM);
        final ModelConfig routerSettings = ResourceUtils.loadModelConfig(routerLLM);

        final boolean isRouterEnabled = StringUtils.isNotBlank(routerLLM);
        final boolean isHybridEnabled = StringUtils.isNotBlank(reasoningLLM) && StringUtils.isNotBlank(toolLLM);

        final LLMModel reasoningModel = buildModel(reasoningLLM, reasoningSettings);
        final LLMModel toolModel = buildModel(toolLLM, toolSettings);
        final LLMModel routerModel = buildModel(routerLLM, routerSettings);
        final LLMModel heavyModel = buildModel(heavyLLM, heavySettings);
        if (isRouterEnabled && isHybridEnabled) {

            return new DefaultAgentEngine(
                    reasoningModel,
                    toolModel,
                    routerModel,
                    heavyModel,
                    tools,
                    contextBuilder,
                    stateStore,
                    invocationLimit,
                    toolRetryLimit,
                    hybrid,
                    thoughtTag,
                    thoughtsEnabled,
                    responseFormat,
                    toolCallingFormat,
                    engineConfig.getRouter() != null,
                    boolValue(agentConfig.getDebug() == null ? null : agentConfig.getDebug().getPromptTraces())
            );
        } else if (!isRouterEnabled) {

        } else if (!isHybridEnabled) {

        } else {

        }



        String thoughtTag = asString(reasoningSettings.get("thought_tag"));
        if (thoughtTag == null || thoughtTag.isBlank()) {
            thoughtTag = "think";
        }
        Boolean thoughtsEnabled = asBoolean(reasoningSettings.get("thoughts_enabled"));
        if (thoughtsEnabled == null) {
            thoughtsEnabled = true;
        }
        String responseFormat = resolveResponseFormat(reasoningSettings.get("format"));
        String toolCallingFormat = resolveResponseFormat(toolSettings.get("format"));
        if (toolCallingFormat != null && !"json".equalsIgnoreCase(toolCallingFormat)) {
            toolCallingFormat = null;
        }



        ToolsConfig toolsConfig = agentConfig.getTools();
        List<String> enabledTools = toolsConfig == null ? null : toolsConfig.getEnabled();
        Map<String, Object> toolConfigs = toolsConfig == null ? Map.of() : toolsConfig.toolConfigs();
        List<AgentTool> tools = ToolRegistry.loadTools(agentName, enabledTools, toolConfigs, rawConfig);

        SessionStore stateStore = buildStateStore(agentConfig.getStateStore());
        boolean hybrid = engineConfig.getTool() != null;
        ContextBuilder contextBuilder = buildContextBuilder(
            agentConfig.getContext(),
            systemMessage,
            summarizerModel,
            tools,
            stateStore,
            hybrid,
            thoughtTag,
            thoughtsEnabled,
            responseFormat,
            resolveModelMaxTokens(summarizerSettings)
        );

        int invocationLimit = engineConfig.getInvocationLimit() == null ? 100 : engineConfig.getInvocationLimit();
        int toolRetryLimit = engineConfig.getToolRetryLimit() == null ? 2 : engineConfig.getToolRetryLimit();
    }

    @Override
    public List<String> agentNames() {
        return null;
    }

    private LLMModel buildModel(String modelName, Map<String, Object> settings) {
        if (modelName)
        String provider = settings.get("provider") == null ? null : settings.get("provider").toString();
        String modelId = settings.get("model") == null ? modelName : settings.get("model").toString();
        String normalizedProvider = ModelFactory.normalizeProvider(provider);
        if ("llamacpp".equals(normalizedProvider) && !modelId.endsWith(".gguf")) {
            modelId = modelId + ".gguf";
        }
        return ModelFactory.buildChatModel(normalizedProvider, modelId, settings);
    }

    private ContextBuilder buildContextBuilder(
        ContextConfig config,
        Message systemMessage,
        LLMModel summarizerModel,
        List<AgentTool> tools,
        SessionStore stateStore,
        boolean hybrid,
        String thoughtTag,
        boolean thoughtsEnabled,
        String responseFormat,
        int modelMaxTokens
    ) {
        String templateName = resolveProtocolTemplate(hybrid, responseFormat);
        String protocolTemplate = PromptTemplates.load(templateName);
        Message protocol = Message.system(
            PromptTemplates.renderProtocol(protocolTemplate, thoughtTag, thoughtsEnabled)
        );
        Message toolMessage = buildToolMessage(tools);
        if (config instanceof LastNContextConfig lastN) {
            return new LastNContextBuilder(systemMessage, protocol, tools, thoughtTag, lastN.getKeepLast()
            );
        }
        SummarizingContextConfig summarizeConfig = config instanceof SummarizingContextConfig summarize
            ? summarize
            : new SummarizingContextConfig();
        int keepLast = 1;
        double triggerThreshold = summarizeConfig.getTriggerThreshold() == null ? 0.8 : summarizeConfig.getTriggerThreshold();
        double recencyThreshold = summarizeConfig.getRecencyThreshold() == null ? 0.3 : summarizeConfig.getRecencyThreshold();
        if (summarizeConfig.getModelMaxTokens() != null) {
            modelMaxTokens = summarizeConfig.getModelMaxTokens();
        }
        return new SummarizingContextBuilder(
            systemMessage,
            protocol,
            toolMessage,
            thoughtTag,
            summarizerModel,
            keepLast,
            triggerThreshold,
            recencyThreshold,
            modelMaxTokens,
            stateStore
        );
    }

    private Message buildToolMessage(List<AgentTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("<AVAILABLE_TOOLS>\n");
        for (AgentTool tool : tools) {
            String line = "- " + tool.name();
            if (tool.description() != null && !tool.description().isBlank()) {
                line += " - " + tool.description();
            }
            builder.append(line).append("\n");
        }
        builder.append("</AVAILABLE_TOOLS>");
        return Message.system(builder.toString());
    }

    private SessionStore buildStateStore(StateStoreConfig config) {
        if (config instanceof MongoStateStoreConfig) {
            LOGGER.warning("Mongo state store not implemented yet; falling back to memory store.");
            return new InMemorySessionStore();
        }
        return new InMemorySessionStore();
    }

    private int resolveModelMaxTokens(Map<String, Object> summarizerSettings) {
        Object numCtx = summarizerSettings.get("num_ctx");
        if (numCtx instanceof Number number) {
            return number.intValue();
        }
        if (numCtx != null) {
            try {
                return Integer.parseInt(numCtx.toString());
            } catch (NumberFormatException ignored) {
                return 8192;
            }
        }
        return 8192;
    }

    private String resolveSummarizerModel(ContextConfig contextConfig, String reasoningName) {
        if (contextConfig instanceof SummarizingContextConfig summarize) {
            String override = summarize.getSummarizerModel();
            if (override != null && !override.isBlank()) {
                return override;
            }
        }
        return reasoningName;
    }

    private String resolveProtocolTemplate(boolean hybrid, String responseFormat) {
        if ("json".equalsIgnoreCase(responseFormat) && hybrid) {
            return "json_reasoner.txt";
        }
        return hybrid ? "reasoner.txt" : "base.txt";
    }

    private String resolveResponseFormat(Object format) {
        if (format == null) {
            return null;
        }
        if (format instanceof Map) {
            return "json";
        }
        String value = format.toString().trim().toLowerCase();
        return switch (value) {
            case "json", "json_object", "json_schema" -> "json";
            default -> value;
        };
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private boolean boolValue(Boolean value) {
        return value != null && value;
    }
}
