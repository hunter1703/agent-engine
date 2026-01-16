package com.localagent.engine.builders;

import com.alibaba.fastjson2.TypeReference;
import com.localagent.engine.beans.config.LastNContextConfig;
import com.localagent.engine.beans.config.ModelConfig;
import com.localagent.engine.beans.config.MongoStateStoreConfig;
import com.localagent.engine.beans.config.StateStoreConfig;
import com.localagent.engine.context.ContextBuilder;
import com.localagent.engine.context.LastNContextBuilder;
import com.localagent.engine.model.LLMModel;
import com.localagent.engine.model.LangChain4JLLMModel;
import com.localagent.engine.state.InMemorySessionStore;
import com.localagent.engine.state.SessionStore;
import com.localagent.engine.tools.AgentTool;
import com.localagent.engine.utils.CollectionUtils;
import com.localagent.engine.utils.JsonUtils;
import com.localagent.engine.utils.TemplateUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public abstract class AbstractAgentBuilder implements AgentBuilder {

    private static final Logger LOGGER = Logger.getLogger(AbstractAgentBuilder.class.getName());
    protected static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";

    private static final Map<String, Object> DEFAULT_JSON_RESPONSE_FORMAT_WITH_THOUGHTS;
    private static final Map<String, Object> DEFAULT_JSON_RESPONSE_FORMAT_WITHOUT_THOUGHTS;

    static {
        DEFAULT_JSON_RESPONSE_FORMAT_WITH_THOUGHTS = JsonUtils.fromFile(Paths.get("schemas/default_json_response_format_with_thoughts.json"), new TypeReference<>() {
        });

        DEFAULT_JSON_RESPONSE_FORMAT_WITHOUT_THOUGHTS = JsonUtils.fromFile(Paths.get("schemas/default_json_response_format_without_thoughts.json"), new TypeReference<>() {
        });
    }

    protected static ContextBuilder buildReasoningContextBuilder(final ModelConfig config, final SessionStore sessionStore, final boolean hybrid, final String systemMessage, final List<AgentTool> tools) {
        String templateName = resolveReasoningProtocolTemplate(hybrid, config.getResponseFormat());
        String protocolMessage = TemplateUtils.renderForName(templateName, Map.of("thoughts_enabled", config.isThoughtsEnabled()));
        return switch (config.getContextConfig()) {
            case LastNContextConfig lastNContextConfig -> new LastNContextBuilder(sessionStore, systemMessage, protocolMessage, tools, lastNContextConfig.getKeepLast());
            default -> throw new IllegalArgumentException("Unknown context config");
        };
    }

    protected static ContextBuilder buildToolAssistantContextBuilder(final ModelConfig config, final SessionStore sessionStore, final String systemMessage, final List<AgentTool> tools) {
        String templateName = resolveToolAssistantProtocolTemplate(config.getResponseFormat());
        String protocolMessage = TemplateUtils.renderForName(templateName, Map.of());
        return switch (config.getContextConfig()) {
            case LastNContextConfig lastNContextConfig -> new LastNContextBuilder(sessionStore, systemMessage, protocolMessage, tools, lastNContextConfig.getKeepLast());
            default -> throw new IllegalArgumentException("Unknown context config");
        };
    }

    protected static ResponseFormat getResponseFormat(final ModelConfig config) {
        if ("json".equals(config.getResponseFormat())) {
            Map<String, Object> responseJsonSchema = config.getResponseJsonSchema();
            if (CollectionUtils.isEmpty(responseJsonSchema)) {
                if (config.isThoughtsEnabled()) {
                    responseJsonSchema = DEFAULT_JSON_RESPONSE_FORMAT_WITH_THOUGHTS;
                } else {
                    responseJsonSchema = DEFAULT_JSON_RESPONSE_FORMAT_WITHOUT_THOUGHTS;
                }
            }
            return new ResponseFormat.Builder().type(ResponseFormatType.JSON).jsonSchema(toJsonSchema(responseJsonSchema)).build();
        } else {
            return new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();
        }
    }

    protected static JsonSchema toJsonSchema(final Map<String, Object> jsonSchemaMap) {
        return JsonSchema.builder().rootElement(buildJsonSchemaElement(jsonSchemaMap)).build();
    }

    protected static JsonSchemaElement buildJsonSchemaElement(final Map<String, Object> jsonSchema) {
        final String type = Objects.requireNonNull(CollectionUtils.getStringValueFromMap(jsonSchema, "type"));
        return switch (type) {
            case "object" -> {
                final JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
                final Map<String, Map<String, Object>> properties = CollectionUtils.getMapFromMap(jsonSchema, "properties");
                for (final Map.Entry<String, Map<String, Object>> fieldProp : properties.entrySet()) {
                    builder.addProperty(fieldProp.getKey(), buildJsonSchemaElement(fieldProp.getValue()));
                }
                yield builder.build();
            }
            case "string" -> JsonStringSchema.builder().build();
            case "integer" -> JsonIntegerSchema.builder().build();
            case "number" -> JsonNumberSchema.builder().build();
            case "boolean" -> JsonBooleanSchema.builder().build();
            default -> throw new IllegalStateException(STR."Unexpected value: \{type}");
        };
    }

    protected static LLMModel buildChatModel(ModelConfig modelConfig) {
        final ModelConfig.Provider provider = ModelConfig.Provider.valueOf(modelConfig.getProvider());
        final ResponseFormat responseFormat = getResponseFormat(modelConfig);
        final ChatModel chatModel = switch (provider) {
            case ModelConfig.Provider.OLLAMA -> buildOllama(modelConfig, responseFormat);
            case ModelConfig.Provider.OPEN_AI, ModelConfig.Provider.LLAMA_CPP -> buildOpenAI(modelConfig, responseFormat);
        };
        return new LangChain4JLLMModel(chatModel, responseFormat, modelConfig.getThoughtsStartTag(), modelConfig.getThoughtsEndTag());
    }

    protected static ChatModel buildOllama(final ModelConfig config, final ResponseFormat responseFormat) {
        return OllamaChatModel.builder().modelName(config.getModel()).baseUrl(config.getBaseUrl())
                .temperature(config.getTemperature()).topK(config.getTopK()).topP(config.getTopP())
                .repeatPenalty(config.getRepeatPenalty()).numPredict(config.getNumPredict())
                .numCtx(config.getMaxContextLength()).stop(config.getStopTokens()).responseFormat(responseFormat).build();
    }

    protected static ChatModel buildOpenAI(final ModelConfig config, final ResponseFormat responseFormat) {
        return OpenAiChatModel.builder().modelName(config.getModel()).baseUrl(config.getBaseUrl())
                .temperature(config.getTemperature()).topP(config.getTopP())
                .stop(config.getStopTokens()).responseFormat(responseFormat).build();
    }

    protected SessionStore buildStateStore(StateStoreConfig config) {
        if (config instanceof MongoStateStoreConfig) {
            LOGGER.warning("Mongo state store not implemented yet; falling back to memory store.");
            return new InMemorySessionStore();
        }
        return new InMemorySessionStore();
    }

    private static String resolveReasoningProtocolTemplate(boolean hybrid, String responseFormat) {
        if ("json".equalsIgnoreCase(responseFormat)) {
            if (hybrid) {
                //TODO: add
                return "protocol/reasoner_json.txt";
            } else {
                return "protocol/simple_json.txt";
            }
        } else {
            if (hybrid) {
                return "protocol/reasoner_text.txt";
            } else {
                return "protocol/simple_text.txt";
            }
        }
    }

    private static String resolveToolAssistantProtocolTemplate(String responseFormat) {
        if ("json".equalsIgnoreCase(responseFormat)) {
           return "protocol/tool_json.txt";
        } else {
            return "protocol/tool_text.txt";
        }
    }
}
