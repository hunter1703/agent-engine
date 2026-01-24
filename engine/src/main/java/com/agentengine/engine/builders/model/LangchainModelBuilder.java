package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ContextManagerConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
import com.agentengine.engine.api.builders.ModelBuilder;
import com.agentengine.engine.api.utils.*;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.model.LangChain4JLLMModel;
import com.agentengine.engine.model.LlamaCppServerUtils;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.ToolRegistry;
import com.alibaba.fastjson2.TypeReference;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class LangchainModelBuilder implements ModelBuilder<LangChain4JLLMModel> {
    private static final Map<String, Object> DEFAULT_JSON_RESPONSE_FORMAT_WITH_THOUGHTS;
    private static final Map<String, Object> DEFAULT_JSON_RESPONSE_FORMAT_WITHOUT_THOUGHTS;

    static {
        DEFAULT_JSON_RESPONSE_FORMAT_WITH_THOUGHTS = JsonUtils.fromJson(
                ResourceUtils.loadResourceAsString("/schemas/hybrid/reasoner_with_thoughts.json"), new TypeReference<>() {
                });

        DEFAULT_JSON_RESPONSE_FORMAT_WITHOUT_THOUGHTS = JsonUtils.fromJson(
                ResourceUtils.loadResourceAsString("/schemas/hybrid/reasoner_without_thoughts.json"), new TypeReference<>() {
                });
    }

    private final ConfigRepository configRepository;
    private final ContextManagerProvider contextManagerProvider;
    private final ToolRegistry toolRegistry;

    public LangchainModelBuilder(final ConfigRepository configRepository,
                                 final ContextManagerProvider contextManagerProvider,
                                 final ToolRegistry toolRegistry) {
        this.configRepository = configRepository;
        this.contextManagerProvider = contextManagerProvider;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public LangChain4JLLMModel build(final String agentName, final AgentModelConfig agentModelConfig) {
        final ModelConfig modelConfig = configRepository.loadModelConfig(agentModelConfig.getModelId());
        final String protocolMessage = buildProtocolMessage(modelConfig);
        final List<Tool> tools = CollectionUtils.nullSafeMutableList(toolRegistry.loadTools(agentName, agentModelConfig.getTools()));
        final ContextManager contextManager = contextManagerProvider.get(agentModelConfig.getContextManagerConfig(), protocolMessage, tools);
        return buildChatModel(modelConfig, contextManager);
    }

    @Override
    public String type() {
        return AgentModelConfig.AgentType.LANGCHAIN.name().toLowerCase();
    }

    private static LangChain4JLLMModel buildChatModel(final ModelConfig modelConfig, final ContextManager contextManager) {
        final ModelConfig.Provider provider = ModelConfig.Provider.valueOf(modelConfig.getType());
        final ResponseFormat responseFormat = getResponseFormat(modelConfig);
        final ChatLanguageModel chatLanguageModel =  switch (provider) {
            case ModelConfig.Provider.OLLAMA -> buildOllama(modelConfig, responseFormat);
            case ModelConfig.Provider.LLAMA_CPP -> {
                LlamaCppServerUtils.ensureRunning(modelConfig);
                yield buildOpenAI(modelConfig, responseFormat);
            }
            case ModelConfig.Provider.OPEN_AI -> buildOpenAI(modelConfig, responseFormat);
        };
        return new LangChain4JLLMModel(chatLanguageModel, responseFormat, modelConfig.isThoughtsEnabled(), modelConfig.getThoughtsStartTag(), modelConfig.getThoughtsEndTag(), contextManager);
    }

    private static ChatLanguageModel buildOllama(final ModelConfig config, final ResponseFormat responseFormat) {
        return OllamaChatModel.builder().modelName(config.getModel()).baseUrl(config.getBaseUrl())
                .temperature(config.getTemperature()).topK(config.getTopK()).topP(config.getTopP())
                .repeatPenalty(config.getRepeatPenalty()).numPredict(config.getNumPredict())
                .numCtx(config.getMaxContextLength()).stop(config.getStopTokens()).responseFormat(responseFormat).build();
    }

    private static ChatLanguageModel buildOpenAI(final ModelConfig config, final ResponseFormat responseFormat) {
        final String format = responseFormat.type() == ResponseFormatType.JSON ? "json" : null;
        return OpenAiChatModel.builder().modelName(config.getModel()).baseUrl(config.getBaseUrl())
                .temperature(config.getTemperature()).topP(config.getTopP()).stop(config.getStopTokens()).responseFormat(format)
                .build();
    }

    protected static ResponseFormat getResponseFormat(final ModelConfig config) {
        if ("json".equals(config.getResponseFormat())) {
            Map<String, Object> responseJsonSchema = config.isThoughtsEnabled()
                    ? DEFAULT_JSON_RESPONSE_FORMAT_WITH_THOUGHTS
                    : DEFAULT_JSON_RESPONSE_FORMAT_WITHOUT_THOUGHTS;
            return new ResponseFormat.Builder().type(ResponseFormatType.JSON).jsonSchema(toJsonSchema(responseJsonSchema))
                    .build();
        } else {
            return new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();
        }
    }

    protected static JsonSchema toJsonSchema(final Map<String, Object> jsonSchemaMap) {
        return JsonSchema.builder().rootElement(buildJsonSchemaElement(jsonSchemaMap)).build();
    }

    protected static JsonSchemaElement buildJsonSchemaElement(final Map<String, Object> jsonSchema) {
        if (jsonSchema == null) {
            return JsonObjectSchema.builder().build();
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
            return JsonObjectSchema.builder().build();
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
            case "null" -> JsonEnumSchema.builder().enumValues("null").build();
            case "" -> {
                if (jsonSchema.containsKey("properties")) {
                    yield buildObjectSchema(jsonSchema);
                }
                if (jsonSchema.containsKey("items")) {
                    yield buildArraySchema(jsonSchema);
                }
                yield JsonObjectSchema.builder().build();
            }
            default -> JsonStringSchema.builder().build();
        };
        final boolean nullable = Boolean.TRUE.equals(jsonSchema.get("nullable"));
        if (nullable) {
            return JsonAnyOfSchema.builder().anyOf(element, JsonEnumSchema.builder().enumValues("null").build()).build();
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

    @SuppressWarnings("unchecked")
    private static JsonSchemaElement buildArraySchema(final Map<String, Object> jsonSchema) {
        final JsonArraySchema.Builder builder = JsonArraySchema.builder();
        final String description = CollectionUtils.getStringValueFromMap(jsonSchema, "description");
        if (StringUtils.isNotBlank(description)) {
            builder.description(description);
        }
        final Object items = jsonSchema.get("items");
        if (items instanceof final Map<?, ?> itemsMap) {
            builder.items(buildJsonSchemaElement((Map<String, Object>) itemsMap));
        } else if (items instanceof final List<?> list) {
            final List<JsonSchemaElement> itemElements = new ArrayList<>();
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> entryMap) {
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

    @SuppressWarnings("unchecked")
    private static List<JsonSchemaElement> buildAnyOfElements(final Object value) {
        if (!(value instanceof final List<?> list)) {
            return List.of();
        }
        final List<JsonSchemaElement> elements = new ArrayList<>();
        for (final Object entry : list) {
            if (entry instanceof final Map<?, ?> map) {
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

    private static String buildProtocolMessage(final ModelConfig config) {
        final String templateName = resolveReasoningProtocolTemplate(config.getResponseFormat());
        final Map<String, Object> context = new HashMap<>();
        context.put("thoughtsEnabled", config.isThoughtsEnabled());
        if ("json".equalsIgnoreCase(config.getResponseFormat())) {
            context.put("response_schema", loadReasonerSchema(config));
        }
        return TemplateUtils.renderTemplateForName(templateName, context);
    }

    private static String resolveReasoningProtocolTemplate(final String responseFormat) {
        if ("json".equalsIgnoreCase(responseFormat)) {
            return "shared/protocol/json.txt";
        } else {
            return "shared/protocol/text.txt";
        }
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
}
