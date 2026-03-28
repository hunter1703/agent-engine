package com.agentengine.runtime.factories.model;

import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.runtime.model.LangChain4jModel;
import com.agentengine.runtime.utils.ModelUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.common.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class LangchainModelFactory extends DelegatingModelFactory<BaseLlm> {
  private static final Map<String, Object> DEFAULT_JSON_RESPONSE_FORMAT;

  static {
    DEFAULT_JSON_RESPONSE_FORMAT = JsonUtils.fromJson(ResourceUtils.loadResourceAsString("/schemas/shared/response_schema.json"),
        new TypeReference<>() {
        });
  }

  @Override
  protected BaseLlm buildDelegate(final ModelConfig modelConfig) {
    final ResponseFormatType responseFormatType = resolveResponseFormatType(modelConfig);
    final ResponseFormat responseFormat = getResponseFormat(responseFormatType);
    final ChatModels models = buildChatModels(modelConfig, responseFormat);
    return new LangChain4jModel(models.chatModel(), models.streamingChatModel(), modelConfig.getModel());
  }

  private record ChatModels(ChatModel chatModel, StreamingChatModel streamingChatModel, ResponseFormat responseFormat) {
  }

  private static ChatModels buildChatModels(final ModelConfig modelConfig, final ResponseFormat responseFormat) {
    final ModelConfig.Provider provider = ModelConfig.Provider.fromType(modelConfig.getType());
    return switch (provider) {
      case ModelConfig.Provider.OLLAMA ->
        new ChatModels(buildOllama(modelConfig, responseFormat), buildOllamaStreaming(modelConfig, responseFormat), responseFormat);
      case ModelConfig.Provider.OPEN_AI_COMPATIBLE -> {
        ModelUtils.ensureRunning(modelConfig);
        yield new ChatModels(buildOpenAI(modelConfig, responseFormat), buildOpenAIStreaming(modelConfig, responseFormat), responseFormat);
      }
      default -> throw new IllegalArgumentException("Unsupported model provider: " + provider);
    };
  }

  private static ChatModel buildOllama(final ModelConfig config, final ResponseFormat responseFormat) {
    return OllamaChatModel.builder().modelName(config.getModel()).baseUrl(config.getBaseUrl()).temperature(config.getTemperature())
        .topK(config.getTopK()).topP(config.getTopP()).repeatPenalty(config.getRepeatPenalty()).numPredict(config.getNumPredict())
        .numCtx(config.getMaxContextLength()).stop(config.getStopTokens()).responseFormat(responseFormat).build();
  }

  private static ChatModel buildOpenAI(final ModelConfig config, final ResponseFormat responseFormat) {
    final String format = responseFormat.type() == ResponseFormatType.JSON ? "json" : null;
    return OpenAiChatModel.builder().modelName(config.getModel()).baseUrl(config.getBaseUrl()).temperature(config.getTemperature())
        .topP(config.getTopP()).stop(config.getStopTokens()).responseFormat(format).returnThinking(true).build();
  }

  private static StreamingChatModel buildOllamaStreaming(final ModelConfig config, final ResponseFormat responseFormat) {
    return OllamaStreamingChatModel.builder().modelName(config.getModel()).baseUrl(config.getBaseUrl()).temperature(config.getTemperature())
        .topK(config.getTopK()).topP(config.getTopP()).repeatPenalty(config.getRepeatPenalty()).numPredict(config.getNumPredict())
        .numCtx(config.getMaxContextLength()).stop(config.getStopTokens()).responseFormat(responseFormat).build();
  }

  private static StreamingChatModel buildOpenAIStreaming(final ModelConfig config, final ResponseFormat responseFormat) {
    final String format = responseFormat.type() == ResponseFormatType.JSON ? "json" : null;
    return OpenAiStreamingChatModel.builder().modelName(config.getModel()).baseUrl(config.getBaseUrl()).temperature(config.getTemperature())
        .topP(config.getTopP()).stop(config.getStopTokens()).responseFormat(format).returnThinking(true).build();
  }

  protected static ResponseFormat getResponseFormat(final ResponseFormatType responseFormatType) {
    return new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();
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
      final List<JsonSchemaElement> itemElements = buildAnyOfElements(list);
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
      if (entry instanceof final Map<?, ?> entryMap) {
        elements.add(buildJsonSchemaElement((Map<String, Object>) entryMap));
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
}
