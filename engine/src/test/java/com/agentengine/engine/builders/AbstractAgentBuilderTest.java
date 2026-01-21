package com.agentengine.engine.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.NoopConfigRepository;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.LastNContextConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.beans.config.MongoStateStoreConfig;
import com.agentengine.engine.api.beans.config.StateStoreConfig;
import com.agentengine.engine.api.beans.config.SummarizingContextConfig;
import com.agentengine.engine.context.ContextBuilder;
import com.agentengine.engine.context.LastNContextBuilder;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.model.LangChain4JLLMModel;
import com.agentengine.engine.state.InMemorySessionStore;
import com.agentengine.engine.api.state.SessionStore;
import com.agentengine.engine.tools.Tool;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AbstractAgentBuilderTest {

  private final TestAgentBuilder builder = new TestAgentBuilder(new NoopConfigRepository());

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
    Map<String, Object> objectSchema = Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")),
        "required", List.of("name"));
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
    Map<String, Object> anyOfSchema = Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "integer")));
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
    Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false, "definitions",
        Map.of("Thing", Map.of("type", "boolean")));

    JsonSchemaElement element = builder.callBuildJsonSchemaElement(schema);

    assertThat(element).isInstanceOf(JsonObjectSchema.class);
  }

  @Test
  void buildJsonSchemaElementHandlesArrayItemsList() {
    Map<String, Object> schema = Map.of("type", "array", "items",
        List.of(Map.of("type", "string"), Map.of("type", "boolean")));

    JsonSchemaElement element = builder.callBuildJsonSchemaElement(schema);

    assertThat(element).isInstanceOf(JsonArraySchema.class);
  }

  @Test
  void buildJsonSchemaElementHandlesImplicitObjectAndNullType() {
    Map<String, Object> implicitObject = Map.of("properties", Map.of("flag", Map.of("type", "boolean")));
    Map<String, Object> nullSchema = Map.of("type", "null");
    Map<String, Object> itemsSchema = Map.of("items", Map.of("type", "string"));

    JsonSchemaElement objectElement = builder.callBuildJsonSchemaElement(implicitObject);
    JsonSchemaElement nullElement = builder.callBuildJsonSchemaElement(nullSchema);
    JsonSchemaElement itemsElement = builder.callBuildJsonSchemaElement(itemsSchema);

    assertThat(objectElement).isInstanceOf(JsonObjectSchema.class);
    assertThat(nullElement).isInstanceOf(JsonEnumSchema.class);
    assertThat(itemsElement).isInstanceOf(JsonArraySchema.class);
  }

  @Test
  void buildJsonSchemaElementSupportsNullSchemaAndOneOf() {
    JsonSchemaElement nullSchema = builder.callBuildJsonSchemaElement(null);
    JsonSchemaElement oneOfSchema = builder
        .callBuildJsonSchemaElement(Map.of("oneOf", List.of(Map.of("type", "string"), Map.of("type", "integer"))));

    assertThat(nullSchema).isInstanceOf(JsonObjectSchema.class);
    assertThat(oneOfSchema).isInstanceOf(JsonAnyOfSchema.class);
  }

  @Test
  void buildJsonSchemaElementHonorsDescriptions() {
    JsonSchemaElement stringElement = builder
        .callBuildJsonSchemaElement(Map.of("type", "string", "description", "desc"));
    JsonSchemaElement intElement = builder.callBuildJsonSchemaElement(Map.of("type", "integer", "description", "desc"));
    JsonSchemaElement numberElement = builder
        .callBuildJsonSchemaElement(Map.of("type", "number", "description", "desc"));
    JsonSchemaElement booleanElement = builder
        .callBuildJsonSchemaElement(Map.of("type", "boolean", "description", "desc"));
    JsonSchemaElement arrayElement = builder
        .callBuildJsonSchemaElement(Map.of("type", "array", "description", "desc", "items", Map.of("type", "string")));
    JsonSchemaElement enumElement = builder
        .callBuildJsonSchemaElement(Map.of("enum", List.of("A"), "description", "desc"));

    assertThat(readDescription(stringElement)).isEqualTo("desc");
    assertThat(readDescription(intElement)).isEqualTo("desc");
    assertThat(readDescription(numberElement)).isEqualTo("desc");
    assertThat(readDescription(booleanElement)).isEqualTo("desc");
    assertThat(readDescription(arrayElement)).isEqualTo("desc");
    assertThat(readDescription(enumElement)).isEqualTo("desc");
  }

  @Test
  void buildJsonSchemaElementHandlesPrimitiveTypes() {
    JsonSchemaElement stringElement = builder.callBuildJsonSchemaElement(Map.of("type", "string"));
    JsonSchemaElement integerElement = builder.callBuildJsonSchemaElement(Map.of("type", "integer"));
    JsonSchemaElement numberElement = builder.callBuildJsonSchemaElement(Map.of("type", "number"));
    JsonSchemaElement booleanElement = builder.callBuildJsonSchemaElement(Map.of("type", "boolean"));

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
    ContextBuilder reasoning = builder.callBuildReasoningContextBuilder(config, sessionStore, true, "system",
        List.of());
    ContextBuilder toolAssistant = builder.callBuildToolAssistantContextBuilder(config, sessionStore, "system",
        List.of());

    assertThat(reasoning).isInstanceOf(LastNContextBuilder.class);
    assertThat(toolAssistant).isInstanceOf(LastNContextBuilder.class);
  }

  @Test
  void buildContextBuildersRejectUnsupportedContextConfig() {
    ModelConfig config = new ModelConfig();
    config.setResponseFormat("json");
    config.setThoughtsEnabled(false);
    config.setContextConfig(new SummarizingContextConfig());

    SessionStore sessionStore = new InMemorySessionStore();

    assertThatThrownBy(() -> builder.callBuildReasoningContextBuilder(config, sessionStore, false, "system", List.of()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("context config");

    assertThatThrownBy(() -> builder.callBuildToolAssistantContextBuilder(config, sessionStore, "system", List.of()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("context config");
  }

  @Test
  void buildContextBuildersUseNonHybridTemplates() {
    ModelConfig config = new ModelConfig();
    config.setResponseFormat("json");
    config.setThoughtsEnabled(false);
    config.setContextConfig(new LastNContextConfig());

    SessionStore sessionStore = new InMemorySessionStore();
    ContextBuilder reasoning = builder.callBuildReasoningContextBuilder(config, sessionStore, false, "system",
        List.of());
    List<Message> prompt = reasoning.buildPrompt("session");

    String protocolMessage = prompt.getFirst().getContent();
    assertThat(protocolMessage).contains("You must return a single JSON object").contains("Return JSON only");
  }

  @Test
  void buildToolAssistantUsesTextTemplate() {
    ModelConfig config = new ModelConfig();
    config.setResponseFormat("text");
    config.setContextConfig(new LastNContextConfig());

    SessionStore sessionStore = new InMemorySessionStore();
    ContextBuilder toolAssistant = builder.callBuildToolAssistantContextBuilder(config, sessionStore, "system",
        List.of());
    List<Message> prompt = toolAssistant.buildPrompt("session");

    assertThat(prompt.getFirst().getContent()).contains("tool-calling model")
        .contains("Use only tools listed inside <AVAILABLE_TOOLS>");
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

    LLMModel openAiModel = builder.callBuildChatModel(openAi);
    LLMModel ollamaModel = builder.callBuildChatModel(ollama);

    assertThat(openAiModel).isInstanceOf(LangChain4JLLMModel.class);
    assertThat(ollamaModel.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
  }

  @Test
  void buildChatModelSupportsLlamaCppProvider() {
    ModelConfig config = new ModelConfig();
    config.setProvider("LLAMA_CPP");
    config.setModel("llama");
    config.setBaseUrl("http://localhost");
    config.setResponseFormat("json");

    LLMModel model = builder.callBuildChatModel(config);

    assertThat(model.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
  }

  @Test
  void buildChatModelUsesTextResponseFormat() {
    ModelConfig config = new ModelConfig();
    config.setProvider("OPEN_AI");
    config.setModel("gpt");
    config.setBaseUrl("http://localhost");
    config.setResponseFormat("text");

    LLMModel model = builder.callBuildChatModel(config);

    assertThat(model.responseFormat().type()).isEqualTo(ResponseFormatType.TEXT);
  }

  private static final class TestAgentBuilder extends AbstractAgentBuilder {
    private TestAgentBuilder(final NoopConfigRepository configRepository) {
      super(configRepository);
    }

    private LLMModel callBuildChatModel(final ModelConfig config) {
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

    private ContextBuilder callBuildReasoningContextBuilder(final ModelConfig config, final SessionStore sessionStore,
        final boolean hybrid, final String systemMessage, final List<Tool> tools) {
      return buildReasoningContextBuilder(config, sessionStore, hybrid, systemMessage, tools);
    }

    private ContextBuilder callBuildToolAssistantContextBuilder(final ModelConfig config,
        final SessionStore sessionStore, final String systemMessage, final List<Tool> tools) {
      return buildToolAssistantContextBuilder(config, sessionStore, systemMessage, tools);
    }

    @Override
    public Agent build(String agentName, AgentConfig agentConfig) {
      return null;
    }

    @Override
    public String type() {
      return null;
    }
  }

  private static String readDescription(final JsonSchemaElement element) {
    for (String methodName : List.of("description", "getDescription")) {
      try {
        Method method = element.getClass().getMethod(methodName);
        Object value = method.invoke(element);
        if (value instanceof String text) {
          return text;
        }
      } catch (ReflectiveOperationException ignored) {
        // ignored
      }
    }
    return null;
  }
}
