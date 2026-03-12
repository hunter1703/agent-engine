package com.agentengine.util.common.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorParallelConfig;
import com.agentengine.engine.api.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.builder.annotations.UiAccessLevel;
import com.agentengine.util.common.builder.annotations.UiPreset;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.adk.JsonBaseModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuilderDefinitionUtilsTest {

  private static final ObjectMapper EXAMPLE_JSON_MAPPER =
      JsonBaseModel.getMapper()
          .copy()
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  @Test
  void shouldOnlyExposeAnnotatedFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    // Explicitly annotated fields are present
    assertThat(definition.layout().fields()).containsKey("/modelId");
    assertThat(definition.layout().fields()).containsKey("/type");
    assertThat(definition.layout().fields()).containsKey("/subAgentIds");

    // Non-annotated fields are absent
    assertThat(definition.layout().fields()).doesNotContainKey("/unknownField");
  }

  @Test
  void shouldBuildExpectedUiForBaseAgentConfig() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    assertThat(definition.layout().presets()).isNull();
    assertThat(definition.layout().fields())
        .containsKeys("/id", "/name", "/type", "/modelId", "/systemPrompt", "/subAgentIds");

    assertThat(definition.layout().fields().get("/id"))
        .extracting(
            LayoutField::label, LayoutField::widget, LayoutField::step, LayoutField::section)
        .containsExactly("ID", "TEXT", "identity", "identity");
    assertThat(definition.layout().fields().get("/name").sensitive()).isTrue();
    assertThat(definition.layout().fields().get("/type").options())
        .isEqualTo(List.of("DEFAULT", "ORCHESTRATOR"));
    assertThat(definition.layout().fields().get("/modelId").lookup())
        .extracting(LayoutLookup::assetType, LayoutLookup::multiSelect)
        .containsExactly("model", null);
    assertThat(definition.layout().fields().get("/systemPrompt"))
        .extracting(
            LayoutField::widget, LayoutField::multiline, LayoutField::rows, LayoutField::sensitive)
        .containsExactly("TEXTAREA", Boolean.TRUE, 6, Boolean.TRUE);
    assertThat(definition.layout().fields().get("/subAgentIds").lookup())
        .extracting(LayoutLookup::assetType, LayoutLookup::multiSelect)
        .containsExactly("agent", Boolean.TRUE);
  }

  @Test
  void shouldIncludeExplicitLookupMetadataForLookupFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    final LayoutField modelId = definition.layout().fields().get("/modelId");
    final LayoutField subAgentIds = definition.layout().fields().get("/subAgentIds");

    assertThat(modelId.lookup())
        .extracting(LayoutLookup::multiSelect, LayoutLookup::assetType)
        .containsExactly(null, "model");
    assertThat(subAgentIds.lookup())
        .extracting(LayoutLookup::multiSelect, LayoutLookup::assetType)
        .containsExactly(Boolean.TRUE, "agent");
  }

  @Test
  void shouldUseEnumBackedOptionsForStringSelectFields() {
    final BuilderDefinition agentDefinition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);
    final BuilderDefinition modelDefinition =
        BuilderDefinitionUtils.generate(ModelConfig.class);

    final LayoutField agentType = agentDefinition.layout().fields().get("/type");
    final LayoutField modelType = modelDefinition.layout().fields().get("/type");
    final Map<String, Object> modelSchema =
        CollectionUtils.getMapFromMap(modelDefinition.schema(), "properties");
    final List<?> modelSchemaOptions =
        CollectionUtils.getValueFromMap(
            CollectionUtils.getMapFromMap(modelSchema, "type"), "enum", List.class);

    assertThat(agentType.options()).isEqualTo(List.of("DEFAULT", "ORCHESTRATOR"));
    assertThat(modelType.options())
        .isEqualTo(List.of("OLLAMA", "OPEN_AI_COMPATIBLE", "GEMINI"));
    assertThat(modelSchemaOptions)
        .isEqualTo(List.of("OLLAMA", "OPEN_AI_COMPATIBLE", "GEMINI"));
  }

  @Test
  void shouldBuildExpectedUiForModelConfig() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(ModelConfig.class);

    assertThat(definition.layout().presets())
        .extracting(LayoutPreset::id)
        .containsExactly("balanced", "focused", "creative");

    assertThat(definition.layout().fields())
        .containsKeys(
            "/type", "/model", "/apiKey", "/serverArgs", "/toolCallingEnabled", "/temperature");
    assertThat(definition.layout().fields().get("/type"))
        .extracting(
            LayoutField::label, LayoutField::widget, LayoutField::step, LayoutField::section)
        .containsExactly("Provider Type", "SELECT", "identity", "identity");
    assertThat(definition.layout().fields().get("/type").options())
        .isEqualTo(List.of("OLLAMA", "OPEN_AI_COMPATIBLE", "GEMINI"));
    assertThat(definition.layout().fields().get("/apiKey").sensitive()).isTrue();
    assertThat(definition.layout().fields().get("/serverArgs"))
        .extracting(
            LayoutField::widget, LayoutField::multiline, LayoutField::rows, LayoutField::collection)
        .containsExactly("TEXTAREA", Boolean.TRUE, 3, Boolean.TRUE);
    assertThat(definition.layout().fields().get("/toolCallingEnabled").widget())
        .isEqualTo("SWITCH");
    assertThat(definition.layout().fields().get("/temperature").numberType()).isEqualTo("decimal");
  }

  @Test
  void shouldExposeExplicitOptionsForEnumSelectFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(OrchestratorAgentConfig.class);

    final LayoutField orchestrationMode = definition.layout().fields().get("/orchestrationMode");

    assertThat(orchestrationMode.widget()).isEqualTo("SELECT");
    assertThat(orchestrationMode.options())
        .isEqualTo(List.of("TRANSFER", "MANAGER", "SEQUENTIAL", "PARALLEL"));
  }

  @Test
  void shouldExposeBuilderRulesForConditionalFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(OrchestratorAgentConfig.class);

    final LayoutField parallel = definition.layout().fields().get("/parallel");
    final LayoutField subAgentIds = definition.layout().fields().get("/subAgentIds");

    assertThat(parallel.rules()).hasSize(1);
    assertThat(parallel.rules().getFirst().effect()).isEqualTo("VISIBLE");
    final List<?> parallelArgs = (List<?>) parallel.rules().getFirst().expr().get("===");
    assertThat(parallelArgs.get(0)).isEqualTo(Map.of("var", "orchestrationMode"));
    assertThat(parallelArgs.get(1)).isEqualTo("PARALLEL");

    assertThat(subAgentIds.rules()).hasSize(1);
    final List<?> subAgentArgs = (List<?>) subAgentIds.rules().getFirst().expr().get("in");
    assertThat(subAgentArgs.get(0)).isEqualTo(Map.of("var", "type"));
    assertThat(subAgentArgs.get(1)).isEqualTo(List.of("ORCHESTRATOR"));
  }

  @Test
  void shouldExposeNestedRulesForParallelConfig() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(OrchestratorParallelConfig.class);

    final LayoutField quorum = definition.layout().fields().get("/quorum");

    assertThat(quorum.rules()).hasSize(1);
    final List<?> quorumArgs = (List<?>) quorum.rules().getFirst().expr().get("===");
    assertThat(quorumArgs.get(0)).isEqualTo(Map.of("var", "stoppingPolicy"));
    assertThat(quorumArgs.get(1)).isEqualTo("QUORUM");
  }

  @Test
  void shouldExposeNestedContextRuntimeAndGuardrailFieldsForBaseAgentConfig() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    assertThat(definition.layout().fields())
        .containsKeys(
            "/contextStrategy/type",
            "/contextStrategy/enabled",
            "/contextStrategy/keepLastTokens",
            "/guardrails/enabled",
            "/guardrails/executionMode",
            "/guardrails/defaultOnError",
            "/guardrails/rules",
            "/guardrails/rules/*/id",
            "/guardrails/rules/*/type",
            "/runtime/resumable",
            "/runtime/maxSteps");

    assertThat(definition.layout().fields().get("/contextStrategy/type").options())
        .isEqualTo(List.of("COMPACTION", "LAST_N"));
    assertThat(definition.layout().fields().get("/contextStrategy/enabled").widget())
        .isEqualTo("SWITCH");
    assertThat(definition.layout().fields().get("/contextStrategy/keepLastTokens").widget())
        .isEqualTo("NUMBER");
    assertThat(definition.layout().fields().get("/guardrails/enabled").widget())
        .isEqualTo("SWITCH");
    assertThat(definition.layout().fields().get("/guardrails/executionMode").widget())
        .isEqualTo("SELECT");
    assertThat(definition.layout().fields().get("/guardrails/executionMode").options())
        .isEqualTo(List.of("SYNC", "OPTIMISTIC"));
    assertThat(definition.layout().fields().get("/guardrails/defaultOnError").widget())
        .isEqualTo("SELECT");
    assertThat(definition.layout().fields().get("/guardrails/defaultOnError").options())
        .isEqualTo(List.of("FAIL_OPEN", "FAIL_CLOSED"));
    assertThat(definition.layout().fields().get("/guardrails/rules").collection()).isTrue();
    assertThat(definition.layout().fields().get("/guardrails/rules/*/type").options())
        .isEqualTo(List.of("TEXT_CONTENT", "TOOL_SAFETY", "RELEVANCE"));
    assertThat(definition.layout().fields().get("/guardrails/rules/*/stage").options())
        .isEqualTo(List.of("INPUT", "TOOL", "OUTPUT"));
    assertThat(definition.layout().fields().get("/guardrails/rules/*/action").options())
        .isEqualTo(List.of("ALLOW", "WARN", "BLOCK", "ESCALATE"));
    assertThat(definition.layout().fields().get("/runtime/resumable").widget())
        .isEqualTo("SWITCH");
    assertThat(definition.layout().fields().get("/runtime/maxSteps").numberType())
        .isEqualTo("integer");

    assertThat(definition.layout().fields().get("/guardrails/rules/*/id").access())
        .extracting(LayoutAccessPolicy::create, LayoutAccessPolicy::edit, LayoutAccessPolicy::view)
        .containsExactly(UiAccessLevel.EDITABLE, UiAccessLevel.EDITABLE, UiAccessLevel.READ_ONLY);
  }

  @Test
  void shouldUseLookupWidgetsForNestedAssetReferenceFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    assertThat(definition.layout().fields().get("/contextStrategy/modelId").lookup())
        .extracting(LayoutLookup::assetType, LayoutLookup::multiSelect)
        .containsExactly("model", null);
    assertThat(definition.layout().fields().get("/guardrails/rules/*/evaluatorModelId").lookup())
        .extracting(LayoutLookup::assetType, LayoutLookup::multiSelect)
        .containsExactly("model", null);
    assertThat(definition.layout().fields().get("/guardrails/rules/*/toolNames").lookup())
        .extracting(LayoutLookup::assetType, LayoutLookup::multiSelect)
        .containsExactly("tool", Boolean.TRUE);
  }

  @Test
  void shouldExposeGuardrailRulesForConditionalFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(OutputRelevanceGuardrailRule.class);

    final LayoutField maxSteeringRetries = definition.layout().fields().get("/maxSteeringRetries");
    final LayoutField recency = definition.layout().fields().get("/recency");

    assertThat(maxSteeringRetries.rules()).hasSize(1);
    @SuppressWarnings("unchecked")
    final Map<String, Object> notInExpr =
        (Map<String, Object>) maxSteeringRetries.rules().getFirst().expr().get("!");
    assertThat(notInExpr).containsKey("in");
    final List<?> notInArgs = (List<?>) notInExpr.get("in");
    assertThat(notInArgs.get(0)).isEqualTo(Map.of("var", "mode"));
    assertThat(notInArgs.get(1)).isEqualTo(List.of("STEER_ONLY"));

    assertThat(recency.rules()).hasSize(1);
    final List<?> recencyArgs = (List<?>) recency.rules().getFirst().expr().get("===");
    assertThat(recencyArgs.get(0)).isEqualTo(Map.of("var", "anchorStrategy"));
    assertThat(recencyArgs.get(1)).isEqualTo("RECENT_USER");
  }

  @Test
  void shouldExposeProviderSpecificRulesForModelFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(ModelConfig.class);

    final LayoutField baseUrl = definition.layout().fields().get("/baseUrl");
    final LayoutField apiKey = definition.layout().fields().get("/apiKey");
    final LayoutField serverCommand = definition.layout().fields().get("/serverCommand");
    final LayoutField serverArgs = definition.layout().fields().get("/serverArgs");
    final LayoutField serverWorkdir = definition.layout().fields().get("/serverWorkdir");

    assertThat(baseUrl.rules()).hasSize(1);
    final List<?> baseUrlArgs = (List<?>) baseUrl.rules().getFirst().expr().get("in");
    assertThat(baseUrlArgs.get(0)).isEqualTo(Map.of("var", "type"));
    assertThat(baseUrlArgs.get(1)).isEqualTo(List.of("OLLAMA", "OPEN_AI_COMPATIBLE"));

    assertThat(apiKey.rules()).hasSize(1);
    final List<?> apiKeyArgs = (List<?>) apiKey.rules().getFirst().expr().get("===");
    assertThat(apiKeyArgs.get(0)).isEqualTo(Map.of("var", "type"));
    assertThat(apiKeyArgs.get(1)).isEqualTo("GEMINI");

    assertThat(serverCommand.rules()).hasSize(1);
    assertThat(serverArgs.rules()).hasSize(1);
    assertThat(serverWorkdir.rules()).hasSize(1);
    assertThat(((List<?>) serverCommand.rules().getFirst().expr().get("===")).get(1))
        .isEqualTo("OPEN_AI_COMPATIBLE");
    assertThat(((List<?>) serverArgs.rules().getFirst().expr().get("===")).get(1))
        .isEqualTo("OPEN_AI_COMPATIBLE");
    assertThat(((List<?>) serverWorkdir.rules().getFirst().expr().get("===")).get(1))
        .isEqualTo("OPEN_AI_COMPATIBLE");
  }

  @Test
  void shouldExposeDynamicSchemaWidgetForDynamicSchemaFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(ToolsConfig.class);

    final LayoutField configs = definition.layout().fields().get("/configs");

    assertThat(configs.widget()).isEqualTo("DYNAMIC_SCHEMA");
    assertThat(configs.dynamicSchema()).isNotNull();
    assertThat(configs.dynamicSchema().url()).isEqualTo("/schemas");
    assertThat(configs.dynamicSchema().method()).isEqualTo("POST");
    assertThat(configs.dynamicSchema().body()).containsEntry("assetType", "tool_configs");
    assertThat(configs.dynamicSchema().body()).containsEntry("assetId", "$item.toolName");
    assertThat(configs.dynamicSchema().body()).containsEntry("contextId", "$.id");
  }

  @Test
  void shouldMarkOnlySemanticallyRequiredFieldsAsRequiredInSchema() {
    final BuilderDefinition modelDefinition =
        BuilderDefinitionUtils.generate(ModelConfig.class);
    final BuilderDefinition toolsDefinition =
        BuilderDefinitionUtils.generate(ToolsConfig.class);
    final BuilderDefinition agentDefinition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    assertThat(requiredFields(modelDefinition.schema())).containsExactlyInAnyOrder("type", "model");
    assertThat(requiredFields(toolsDefinition.schema()))
        .containsExactly("toolName");

    final Map<String, Object> agentProperties =
        CollectionUtils.getMapFromMap(agentDefinition.schema(), "properties");
    final Map<String, Object> contextStrategySchema =
        CollectionUtils.getMapFromMap(agentProperties, "contextStrategy");
    final Map<String, Object> guardrailsSchema =
        CollectionUtils.getMapFromMap(agentProperties, "guardrails");
    final Map<String, Object> guardrailsProperties =
        CollectionUtils.getMapFromMap(guardrailsSchema, "properties");
    final Map<String, Object> rulesSchema =
        CollectionUtils.getMapFromMap(guardrailsProperties, "rules");
    final Map<String, Object> ruleItemSchema = CollectionUtils.getMapFromMap(rulesSchema, "items");

    assertThat(requiredFields(contextStrategySchema)).containsExactly("type");
    assertThat(requiredFields(ruleItemSchema)).containsExactlyInAnyOrder("id", "type");
  }

  @Test
  void shouldOmitWidgetForSchemaDrivenFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    assertThat(definition.layout().fields().get("/tools").widget()).isNull();
    assertThat(definition.layout().fields().get("/contextStrategy").widget()).isNull();
    assertThat(definition.layout().fields().get("/subAgentIds").widget()).isEqualTo("LOOKUP");
  }

  @Test
  void shouldMarkCollectionFieldsAsCollection() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    assertThat(definition.layout().fields().get("/subAgentIds").collection()).isTrue();
    assertThat(definition.layout().fields().get("/modelId").collection()).isNull();
  }

  @Test
  void shouldMarkSensitiveFieldsFromSecureAnnotation() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    assertThat(definition.layout().fields().get("/systemPrompt").sensitive()).isTrue();
    assertThat(definition.layout().fields().get("/modelId").sensitive()).isNull();
  }

  @Test
  void shouldExposeTextareaAttributesForMultilineFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(BaseAgentConfig.class);

    final LayoutField systemPrompt = definition.layout().fields().get("/systemPrompt");

    assertThat(systemPrompt.widget()).isEqualTo("TEXTAREA");
    assertThat(systemPrompt.multiline()).isTrue();
    assertThat(systemPrompt.rows()).isEqualTo(6);
  }

  @Test
  void shouldIndexPresetsByJsonPathForRootAndNestedTypes() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate(NestedPresetRoot.class);

    assertThat(definition.layout().presets())
        .extracting(LayoutPreset::id)
        .containsExactly("root-preset");
    assertThat(definition.layout().fields().get("/nested").presets())
        .extracting(LayoutPreset::id)
        .containsExactly("nested-preset");
  }

  @Test
  void shouldGenerateBuilderUiExampleJsonSnapshots() throws IOException {
    writeExampleJson("agent.json", BuilderDefinitionUtils.generate(BaseAgentConfig.class));
    writeExampleJson("model.json", BuilderDefinitionUtils.generate(ModelConfig.class));
  }

  private static void writeExampleJson(final String fileName, final BuilderDefinition definition)
      throws IOException {
    final Path repoRoot = findRepoRoot();
    final Path path = repoRoot.resolve("docs/examples").resolve(fileName);
    Files.createDirectories(path.getParent());
    final String json =
        EXAMPLE_JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(definition);
    Files.writeString(path, json);

    assertThat(Files.readString(path)).isEqualTo(json);
  }

  private static List<String> requiredFields(final Map<String, Object> schema) {
    @SuppressWarnings("unchecked")
    final List<Object> raw = CollectionUtils.getValueFromMap(schema, "required", List.class);
    if (raw == null) {
      return List.of();
    }
    return raw.stream().map(String::valueOf).toList();
  }

  private static Path findRepoRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve(".git"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException(
        "Unable to locate repository root from " + System.getProperty("user.dir"));
  }

  @UiPreset(id = "root-preset", label = "Root Preset", preset = "{\"nested\":{\"value\":\"root\"}}")
  private static final class NestedPresetRoot {
    @com.agentengine.util.common.builder.annotations.UiField private NestedPresetChild nested;
  }

  @UiPreset(id = "nested-preset", label = "Nested Preset", preset = "{\"value\":\"child\"}")
  private static final class NestedPresetChild {
    @com.agentengine.util.common.builder.annotations.UiText private String value;
  }
}
