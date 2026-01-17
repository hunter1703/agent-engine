package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.beans.config.LastNContextConfig;
import com.agentengine.engine.beans.config.ModelConfig;
import com.agentengine.engine.beans.config.MongoStateStoreConfig;
import com.agentengine.engine.beans.config.StateStoreConfig;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.state.InMemorySessionStore;
import com.agentengine.engine.state.SessionStore;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AbstractAgentBuilderTest {

  private final TestAgentBuilder builder = new TestAgentBuilder();

  @Test
  void getResponseFormatUsesJsonWhenConfigured() {
    ModelConfig config = new ModelConfig();
    config.setResponseFormat("json");
    config.setThoughtsEnabled(true);

    ResponseFormat format = builder.callGetResponseFormat(config);

    assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
  }

  @Test
  void getResponseFormatUsesTextByDefault() {
    ModelConfig config = new ModelConfig();
    config.setResponseFormat("text");

    ResponseFormat format = builder.callGetResponseFormat(config);

    assertThat(format.type()).isEqualTo(ResponseFormatType.TEXT);
  }

  @Test
  void buildJsonSchemaElementHandlesObjectsArraysEnumsAndNullable() {
    Map<String, Object> objectSchema =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("name", Map.of("type", "string")),
            "required",
            List.of("name"));
    JsonSchemaElement objectElement = builder.callBuildJsonSchemaElement(objectSchema);

    Map<String, Object> arraySchema = Map.of("type", "array", "items", Map.of("type", "boolean"));
    JsonSchemaElement arrayElement = builder.callBuildJsonSchemaElement(arraySchema);

    Map<String, Object> enumSchema = Map.of("enum", List.of("A", "B"));
    JsonSchemaElement enumElement = builder.callBuildJsonSchemaElement(enumSchema);

    Map<String, Object> nullableSchema = Map.of("type", "string", "nullable", true);
    JsonSchemaElement nullableElement = builder.callBuildJsonSchemaElement(nullableSchema);

    assertThat(objectElement).isInstanceOf(JsonObjectSchema.class);
    assertThat(arrayElement).isInstanceOf(JsonArraySchema.class);
    assertThat(enumElement).isInstanceOf(JsonEnumSchema.class);
    assertThat(nullableElement).isInstanceOf(JsonAnyOfSchema.class);
  }

  @Test
  void buildJsonSchemaElementSupportsAnyOfAndConst() {
    Map<String, Object> anyOfSchema =
        Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "integer")));
    JsonSchemaElement anyOfElement = builder.callBuildJsonSchemaElement(anyOfSchema);

    Map<String, Object> constSchema = Map.of("const", "fixed");
    JsonSchemaElement constElement = builder.callBuildJsonSchemaElement(constSchema);

    assertThat(anyOfElement).isInstanceOf(JsonAnyOfSchema.class);
    assertThat(constElement).isInstanceOf(JsonEnumSchema.class);
  }

  @Test
  void buildJsonSchemaElementSupportsRefAndTypeList() {
    Map<String, Object> refSchema = Map.of("$ref", "#/definitions/Thing");
    Map<String, Object> typeListSchema = Map.of("type", List.of("string", "integer"));

    JsonSchemaElement refElement = builder.callBuildJsonSchemaElement(refSchema);
    JsonSchemaElement typeListElement = builder.callBuildJsonSchemaElement(typeListSchema);

    assertThat(refElement.getClass().getSimpleName()).contains("Reference");
    assertThat(typeListElement).isInstanceOf(JsonAnyOfSchema.class);
  }

  @Test
  void buildJsonSchemaElementHandlesAllOfFallback() {
    Map<String, Object> allOfSchema = Map.of("allOf", List.of(Map.of("type", "string")));

    JsonSchemaElement element = builder.callBuildJsonSchemaElement(allOfSchema);

    assertThat(element).isInstanceOf(JsonObjectSchema.class);
  }

  @Test
  void buildJsonSchemaElementHandlesAdditionalPropertiesAndDefinitions() {
    Map<String, Object> schema =
        Map.of(
            "type",
            "object",
            "additionalProperties",
            false,
            "definitions",
            Map.of("Thing", Map.of("type", "boolean")));

    JsonSchemaElement element = builder.callBuildJsonSchemaElement(schema);

    assertThat(element).isInstanceOf(JsonObjectSchema.class);
  }

  @Test
  void buildJsonSchemaElementHandlesArrayItemsList() {
    Map<String, Object> schema =
        Map.of(
            "type", "array", "items", List.of(Map.of("type", "string"), Map.of("type", "boolean")));

    JsonSchemaElement element = builder.callBuildJsonSchemaElement(schema);

    assertThat(element).isInstanceOf(JsonArraySchema.class);
  }

  @Test
  void buildJsonSchemaElementHandlesImplicitObjectAndNullType() {
    Map<String, Object> implicitObject =
        Map.of("properties", Map.of("flag", Map.of("type", "boolean")));
    Map<String, Object> nullSchema = Map.of("type", "null");

    JsonSchemaElement objectElement = builder.callBuildJsonSchemaElement(implicitObject);
    JsonSchemaElement nullElement = builder.callBuildJsonSchemaElement(nullSchema);

    assertThat(objectElement).isInstanceOf(JsonObjectSchema.class);
    assertThat(nullElement).isInstanceOf(JsonEnumSchema.class);
  }

  @Test
  void buildJsonSchemaElementHandlesPrimitiveTypes() {
    JsonSchemaElement stringElement = builder.callBuildJsonSchemaElement(Map.of("type", "string"));
    JsonSchemaElement integerElement =
        builder.callBuildJsonSchemaElement(Map.of("type", "integer"));
    JsonSchemaElement numberElement = builder.callBuildJsonSchemaElement(Map.of("type", "number"));
    JsonSchemaElement booleanElement =
        builder.callBuildJsonSchemaElement(Map.of("type", "boolean"));

    assertThat(stringElement.getClass().getSimpleName()).contains("String");
    assertThat(integerElement.getClass().getSimpleName()).contains("Integer");
    assertThat(numberElement.getClass().getSimpleName()).contains("Number");
    assertThat(booleanElement.getClass().getSimpleName()).contains("Boolean");
  }

  @Test
  void buildStateStoreFallsBackToInMemoryForMongo() {
    SessionStore sessionStore = builder.callBuildStateStore(new MongoStateStoreConfig());

    assertThat(sessionStore).isInstanceOf(InMemorySessionStore.class);
  }

  @Test
  void buildContextBuildersUseLastNConfig() {
    ModelConfig config = new ModelConfig();
    config.setResponseFormat("json");
    config.setThoughtsEnabled(true);
    config.setContextConfig(new LastNContextConfig());

    SessionStore sessionStore = new InMemorySessionStore();
    ContextBuilder reasoning =
        builder.callBuildReasoningContextBuilder(config, sessionStore, true, "system", List.of());
    ContextBuilder toolAssistant =
        builder.callBuildToolAssistantContextBuilder(config, sessionStore, "system", List.of());

    assertThat(reasoning).isInstanceOf(com.agentengine.engine.context.LastNContextBuilder.class);
    assertThat(toolAssistant)
        .isInstanceOf(com.agentengine.engine.context.LastNContextBuilder.class);
  }

  @Test
  void buildChatModelBuildsLangChainModelForProviders() {
    ModelConfig openAi = new ModelConfig();
    openAi.setProvider("OPEN_AI");
    openAi.setModel("gpt");
    openAi.setBaseUrl("http://localhost");
    openAi.setResponseFormat("text");

    ModelConfig ollama = new ModelConfig();
    ollama.setProvider("OLLAMA");
    ollama.setModel("llama");
    ollama.setBaseUrl("http://localhost");
    ollama.setResponseFormat("json");

    com.agentengine.engine.model.LLMModel openAiModel = builder.callBuildChatModel(openAi);
    com.agentengine.engine.model.LLMModel ollamaModel = builder.callBuildChatModel(ollama);

    assertThat(openAiModel).isInstanceOf(com.agentengine.engine.model.LangChain4JLLMModel.class);
    assertThat(ollamaModel.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
  }

  @Test
  void buildChatModelUsesTextResponseFormat() {
    ModelConfig config = new ModelConfig();
    config.setProvider("OPEN_AI");
    config.setModel("gpt");
    config.setBaseUrl("http://localhost");
    config.setResponseFormat("text");

    com.agentengine.engine.model.LLMModel model = builder.callBuildChatModel(config);

    assertThat(model.responseFormat().type()).isEqualTo(ResponseFormatType.TEXT);
  }

  private static final class TestAgentBuilder extends AbstractAgentBuilder {
    private com.agentengine.engine.model.LLMModel callBuildChatModel(final ModelConfig config) {
      return buildChatModel(config);
    }

    private ResponseFormat callGetResponseFormat(final ModelConfig config) {
      return getResponseFormat(config);
    }

    private JsonSchemaElement callBuildJsonSchemaElement(final Map<String, Object> jsonSchema) {
      return buildJsonSchemaElement(jsonSchema);
    }

    private SessionStore callBuildStateStore(final StateStoreConfig config) {
      return buildStateStore(config);
    }

    private ContextBuilder callBuildReasoningContextBuilder(
        final ModelConfig config,
        final SessionStore sessionStore,
        final boolean hybrid,
        final String systemMessage,
        final List<com.agentengine.engine.tools.AgentTool> tools) {
      return buildReasoningContextBuilder(config, sessionStore, hybrid, systemMessage, tools);
    }

    private ContextBuilder callBuildToolAssistantContextBuilder(
        final ModelConfig config,
        final SessionStore sessionStore,
        final String systemMessage,
        final List<com.agentengine.engine.tools.AgentTool> tools) {
      return buildToolAssistantContextBuilder(config, sessionStore, systemMessage, tools);
    }

    @Override
    public com.agentengine.engine.AgentEngine build(
        String agentName, com.agentengine.engine.beans.config.AgentConfig agentConfig) {
      return null;
    }

    @Override
    public List<String> agentNames() {
      return List.of();
    }
  }
}
