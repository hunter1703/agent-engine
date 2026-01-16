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
import com.localagent.engine.utils.ResourceUtils;
import com.localagent.engine.utils.StringUtils;
import com.localagent.engine.utils.TemplateUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public abstract class AbstractAgentBuilder implements AgentBuilder {

    private static final Logger LOGGER = Logger.getLogger(AbstractAgentBuilder.class.getName());
    protected static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";

    private static final Map<String, Object> DEFAULT_JSON_RESPONSE_FORMAT_WITH_THOUGHTS;
    private static final Map<String, Object> DEFAULT_JSON_RESPONSE_FORMAT_WITHOUT_THOUGHTS;

    static {
        DEFAULT_JSON_RESPONSE_FORMAT_WITH_THOUGHTS = JsonUtils.fromJson(
            ResourceUtils.loadResourceAsString("/schemas/hybrid/reasoner_with_thoughts.json"),
            new TypeReference<>() {}
        );

        DEFAULT_JSON_RESPONSE_FORMAT_WITHOUT_THOUGHTS = JsonUtils.fromJson(
            ResourceUtils.loadResourceAsString("/schemas/hybrid/reasoner_without_thoughts.json"),
            new TypeReference<>() {}
        );
    }

    protected static ContextBuilder buildReasoningContextBuilder(final ModelConfig config, final SessionStore sessionStore, final boolean hybrid, final String systemMessage, final List<AgentTool> tools) {
        final String templateName = resolveReasoningProtocolTemplate(hybrid, config.getResponseFormat());
        final Map<String, Object> context = new java.util.HashMap<>();
        context.put("thoughts_enabled", config.isThoughtsEnabled());
        if ("json".equalsIgnoreCase(config.getResponseFormat())) {
            context.put("response_schema", loadReasonerSchema(config));
        }
        final String protocolMessage = TemplateUtils.renderForName(templateName, context);
        return switch (config.getContextConfig()) {
            case LastNContextConfig lastNContextConfig -> new LastNContextBuilder(sessionStore, systemMessage, protocolMessage, tools, lastNContextConfig.getKeepLast());
            default -> throw new IllegalArgumentException("Unknown context config");
        };
    }

    protected static ContextBuilder buildToolAssistantContextBuilder(final ModelConfig config, final SessionStore sessionStore, final String systemMessage, final List<AgentTool> tools) {
        final String templateName = resolveToolAssistantProtocolTemplate(config.getResponseFormat());
        final Map<String, Object> context = new java.util.HashMap<>();
        if ("json".equalsIgnoreCase(config.getResponseFormat())) {
            context.put("tool_schema", loadToolCallSchema());
        }
        final String protocolMessage = TemplateUtils.renderForName(templateName, context);
        return switch (config.getContextConfig()) {
            case LastNContextConfig lastNContextConfig -> new LastNContextBuilder(sessionStore, systemMessage, protocolMessage, tools, lastNContextConfig.getKeepLast());
            default -> throw new IllegalArgumentException("Unknown context config");
        };
    }

    protected static ResponseFormat getResponseFormat(final ModelConfig config) {
        if ("json".equals(config.getResponseFormat())) {
            Map<String, Object> responseJsonSchema = config.isThoughtsEnabled()
                ? DEFAULT_JSON_RESPONSE_FORMAT_WITH_THOUGHTS
                : DEFAULT_JSON_RESPONSE_FORMAT_WITHOUT_THOUGHTS;
            return new ResponseFormat.Builder().type(ResponseFormatType.JSON).jsonSchema(toJsonSchema(responseJsonSchema)).build();
        } else {
            return new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();
        }
    }

    protected static JsonSchema toJsonSchema(final Map<String, Object> jsonSchemaMap) {
        return JsonSchema.builder().rootElement(buildJsonSchemaElement(jsonSchemaMap)).build();
    }

    protected static JsonSchemaElement buildJsonSchemaElement(final Map<String, Object> jsonSchema) {
        if (jsonSchema == null) {
            return JsonRawSchema.from("{}");
        }
        final Object refValue = jsonSchema.get("$ref");
        if (refValue instanceof final String reference) {
            return JsonReferenceSchema.builder().reference(reference).build();
        }
        final List<JsonSchemaElement> anyOfElements = buildAnyOfElements(jsonSchema.get("anyOf"));
        final List<JsonSchemaElement> oneOfElements = buildAnyOfElements(jsonSchema.get("oneOf"));
        final List<JsonSchemaElement> allOfElements = buildAnyOfElements(jsonSchema.get("allOf"));
        if (!anyOfElements.isEmpty()) {
            return JsonAnyOfSchema.builder().anyOf(anyOfElements).build();
        }
        if (!oneOfElements.isEmpty()) {
            return JsonAnyOfSchema.builder().anyOf(oneOfElements).build();
        }
        if (!allOfElements.isEmpty()) {
            return JsonRawSchema.from(JsonUtils.toJson(jsonSchema));
        }
        final JsonSchemaElement enumSchema = buildEnumSchema(jsonSchema);
        if (enumSchema != null) {
            return enumSchema;
        }
        final Object typeValue = jsonSchema.get("type");
        if (typeValue instanceof final List<?> listTypes) {
            final List<JsonSchemaElement> typeElements = new ArrayList<>();
            for (final Object item : listTypes) {
                if (item instanceof final String typeItem) {
                    final Map<String, Object> nested = new HashMap<>(jsonSchema);
                    nested.put("type", typeItem);
                    nested.remove("anyOf");
                    nested.remove("oneOf");
                    nested.remove("allOf");
                    typeElements.add(buildJsonSchemaElement(nested));
                }
            }
            if (!typeElements.isEmpty()) {
                return JsonAnyOfSchema.builder().anyOf(typeElements).build();
            }
        }
        final String type = typeValue instanceof final String typeString ? typeString : null;
        final JsonSchemaElement element = switch (type == null ? "" : type) {
            case "object" -> buildObjectSchema(jsonSchema);
            case "array" -> buildArraySchema(jsonSchema);
            case "string" -> buildStringSchema(jsonSchema);
            case "integer" -> buildIntegerSchema(jsonSchema);
            case "number" -> buildNumberSchema(jsonSchema);
            case "boolean" -> buildBooleanSchema(jsonSchema);
            case "null" -> new JsonNullSchema();
            case "" -> {
                if (jsonSchema.containsKey("properties")) {
                    yield buildObjectSchema(jsonSchema);
                }
                if (jsonSchema.containsKey("items")) {
                    yield buildArraySchema(jsonSchema);
                }
                yield JsonRawSchema.from(JsonUtils.toJson(jsonSchema));
            }
            default -> JsonRawSchema.from(JsonUtils.toJson(jsonSchema));
        };
        final boolean nullable = Boolean.TRUE.equals(jsonSchema.get("nullable"));
        if (nullable) {
            return JsonAnyOfSchema.builder().anyOf(element, new JsonNullSchema()).build();
        }
        return element;
    }

    private static JsonSchemaElement buildObjectSchema(final Map<String, Object> jsonSchema) {
        final JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        final String description = CollectionUtils.getStringValueFromMap(jsonSchema, "description");
        if (StringUtils.isNotBlank(description)) {
            builder.description(description);
        }
        final Map<String, Map<String, Object>> properties = CollectionUtils.getMapFromMap(jsonSchema, "properties");
        if (!CollectionUtils.isEmpty(properties)) {
            for (final Map.Entry<String, Map<String, Object>> fieldProp : properties.entrySet()) {
                builder.addProperty(fieldProp.getKey(), buildJsonSchemaElement(fieldProp.getValue()));
            }
        }
        final List<String> required = getStringList(jsonSchema.get("required"));
        if (!required.isEmpty()) {
            builder.required(required);
        }
        final Object additionalProperties = jsonSchema.get("additionalProperties");
        if (additionalProperties instanceof final Boolean allowed) {
            builder.additionalProperties(allowed);
        }
        final Map<String, Map<String, Object>> definitions = CollectionUtils.getMapFromMap(jsonSchema, "definitions");
        if (!CollectionUtils.isEmpty(definitions)) {
            final Map<String, JsonSchemaElement> definitionSchemas = new HashMap<>();
            for (final Map.Entry<String, Map<String, Object>> entry : definitions.entrySet()) {
                definitionSchemas.put(entry.getKey(), buildJsonSchemaElement(entry.getValue()));
            }
            builder.definitions(definitionSchemas);
        }
        return builder.build();
    }

    private static JsonSchemaElement buildArraySchema(final Map<String, Object> jsonSchema) {
        final JsonArraySchema.Builder builder = JsonArraySchema.builder();
        final String description = CollectionUtils.getStringValueFromMap(jsonSchema, "description");
        if (StringUtils.isNotBlank(description)) {
            builder.description(description);
        }
        final Object items = jsonSchema.get("items");
        if (items instanceof final Map<?, ?> itemsMap) {
            //noinspection unchecked
            builder.items(buildJsonSchemaElement((Map<String, Object>) itemsMap));
        } else if (items instanceof final List<?> list) {
            final List<JsonSchemaElement> itemElements = new ArrayList<>();
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> entryMap) {
                    //noinspection unchecked
                    itemElements.add(buildJsonSchemaElement((Map<String, Object>) entryMap));
                }
            }
            if (!itemElements.isEmpty()) {
                builder.items(JsonAnyOfSchema.builder().anyOf(itemElements).build());
            }
        }
        return builder.build();
    }

    private static JsonSchemaElement buildStringSchema(final Map<String, Object> jsonSchema) {
        final String description = CollectionUtils.getStringValueFromMap(jsonSchema, "description");
        if (StringUtils.isNotBlank(description)) {
            return JsonStringSchema.builder().description(description).build();
        }
        return JsonStringSchema.builder().build();
    }

    private static JsonSchemaElement buildIntegerSchema(final Map<String, Object> jsonSchema) {
        final String description = CollectionUtils.getStringValueFromMap(jsonSchema, "description");
        if (StringUtils.isNotBlank(description)) {
            return JsonIntegerSchema.builder().description(description).build();
        }
        return JsonIntegerSchema.builder().build();
    }

    private static JsonSchemaElement buildNumberSchema(final Map<String, Object> jsonSchema) {
        final String description = CollectionUtils.getStringValueFromMap(jsonSchema, "description");
        if (StringUtils.isNotBlank(description)) {
            return JsonNumberSchema.builder().description(description).build();
        }
        return JsonNumberSchema.builder().build();
    }

    private static JsonSchemaElement buildBooleanSchema(final Map<String, Object> jsonSchema) {
        final String description = CollectionUtils.getStringValueFromMap(jsonSchema, "description");
        if (StringUtils.isNotBlank(description)) {
            return JsonBooleanSchema.builder().description(description).build();
        }
        return JsonBooleanSchema.builder().build();
    }

    private static JsonSchemaElement buildEnumSchema(final Map<String, Object> jsonSchema) {
        final Object enumValue = jsonSchema.get("enum");
        if (enumValue instanceof final List<?> list) {
            final List<String> values = new ArrayList<>();
            for (final Object entry : list) {
                if (entry != null) {
                    values.add(entry.toString());
                }
            }
            if (!values.isEmpty()) {
                final JsonEnumSchema.Builder builder = JsonEnumSchema.builder().enumValues(values);
                final String description = CollectionUtils.getStringValueFromMap(jsonSchema, "description");
                if (StringUtils.isNotBlank(description)) {
                    builder.description(description);
                }
                return builder.build();
            }
        }
        if (jsonSchema.containsKey("const")) {
            final Object constValue = jsonSchema.get("const");
            if (constValue != null) {
                return JsonEnumSchema.builder().enumValues(constValue.toString()).build();
            }
        }
        return null;
    }

    private static List<JsonSchemaElement> buildAnyOfElements(final Object value) {
        if (!(value instanceof final List<?> list)) {
            return List.of();
        }
        final List<JsonSchemaElement> elements = new ArrayList<>();
        for (final Object entry : list) {
            if (entry instanceof final Map<?, ?> map) {
                //noinspection unchecked
                elements.add(buildJsonSchemaElement((Map<String, Object>) map));
            }
        }
        return elements;
    }

    private static List<String> getStringList(final Object value) {
        if (!(value instanceof final List<?> list)) {
            return List.of();
        }
        final List<String> strings = new ArrayList<>();
        for (final Object entry : list) {
            if (entry != null) {
                strings.add(entry.toString());
            }
        }
        return strings;
    }

    private static String loadReasonerSchema(final ModelConfig config) {
        if (!"json".equalsIgnoreCase(config.getResponseFormat())) {
            return "";
        }
        String path = config.isThoughtsEnabled()
            ? "/schemas/hybrid/reasoner_with_thoughts.json"
            : "/schemas/hybrid/reasoner_without_thoughts.json";
        return ResourceUtils.loadResourceAsString(path);
    }

    private static String loadToolCallSchema() {
        return ResourceUtils.loadResourceAsString("/schemas/hybrid/tool_call_schema.json");
    }

    protected static LLMModel buildChatModel(final ModelConfig modelConfig) {
        final ModelConfig.Provider provider = ModelConfig.Provider.valueOf(modelConfig.getProvider());
        final ResponseFormat responseFormat = getResponseFormat(modelConfig);
        final ChatModel chatModel = switch (provider) {
            case ModelConfig.Provider.OLLAMA -> buildOllama(modelConfig, responseFormat);
            case ModelConfig.Provider.OPEN_AI, ModelConfig.Provider.LLAMA_CPP -> buildOpenAI(modelConfig, responseFormat);
        };
        return new LangChain4JLLMModel(chatModel, responseFormat, modelConfig.isThoughtsEnabled(), modelConfig.getThoughtsStartTag(), modelConfig.getThoughtsEndTag());
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

    protected SessionStore buildStateStore(final StateStoreConfig config) {
        if (config instanceof MongoStateStoreConfig) {
            LOGGER.warning("Mongo state store not implemented yet; falling back to memory store.");
            return new InMemorySessionStore();
        }
        return new InMemorySessionStore();
    }

    private static String resolveReasoningProtocolTemplate(final boolean hybrid, final String responseFormat) {
        if ("json".equalsIgnoreCase(responseFormat)) {
            if (hybrid) {
                return "hybrid/reasoner_json.txt";
            } else {
                return "simple/assistant_json.txt";
            }
        } else {
            if (hybrid) {
                return "hybrid/reasoner_text.txt";
            } else {
                return "simple/assistant_text.txt";
            }
        }
    }

    private static String resolveToolAssistantProtocolTemplate(final String responseFormat) {
        if ("json".equalsIgnoreCase(responseFormat)) {
           return "hybrid/tool_assistant_json.txt";
        } else {
            return "hybrid/tool_assistant_text.txt";
        }
    }
}
