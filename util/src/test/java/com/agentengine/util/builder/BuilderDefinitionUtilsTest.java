package com.agentengine.util.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.beans.config.OrchestratorAgentConfig;
import com.agentengine.engine.api.beans.config.OrchestratorParallelConfig;
import com.agentengine.engine.api.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.util.CollectionUtils;
import com.agentengine.util.JsonUtils;
import com.agentengine.util.builder.annotations.UiPreset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuilderDefinitionUtilsTest {

  @Test
  void shouldOnlyExposeAnnotatedFields() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("agent", BaseAgentConfig.class);

    // Explicitly annotated fields are present
    assertThat(definition.ui().fields()).containsKey("/modelId");
    assertThat(definition.ui().fields()).containsKey("/type");
    assertThat(definition.ui().fields()).containsKey("/subAgentIds");

    // Non-annotated fields are absent
    assertThat(definition.ui().fields()).doesNotContainKey("/unknownField");
  }

  @Test
  void shouldBuildExpectedUiForBaseAgentConfig() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("agent", BaseAgentConfig.class);

    assertThat(definition.ui().presets()).isNull();
    assertThat(definition.ui().fields()).containsKeys(
        "/id", "/name", "/type", "/modelId", "/systemPrompt", "/subAgentIds");

    assertThat(definition.ui().fields().get("/id"))
        .extracting(LayoutField::label, LayoutField::widget, LayoutField::step, LayoutField::section)
        .containsExactly("ID", "TEXT", "identity", "identity");
    assertThat(definition.ui().fields().get("/name").sensitive()).isTrue();
    assertThat(definition.ui().fields().get("/type").options())
        .isEqualTo(List.of("default", "orchestrator"));
    assertThat(definition.ui().fields().get("/modelId").lookup())
        .extracting(LayoutLookup::assetType, LayoutLookup::multiSelect)
        .containsExactly("model", null);
    assertThat(definition.ui().fields().get("/systemPrompt"))
        .extracting(LayoutField::widget, LayoutField::multiline, LayoutField::rows, LayoutField::sensitive)
        .containsExactly("TEXTAREA", Boolean.TRUE, 6, Boolean.TRUE);
    assertThat(definition.ui().fields().get("/subAgentIds").lookup())
        .extracting(LayoutLookup::assetType, LayoutLookup::multiSelect)
        .containsExactly("agent", Boolean.TRUE);
  }

  @Test
  void shouldIncludeExplicitLookupMetadataForLookupFields() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("agent", BaseAgentConfig.class);

    final LayoutField modelId = definition.ui().fields().get("/modelId");
    final LayoutField subAgentIds = definition.ui().fields().get("/subAgentIds");

    assertThat(modelId.lookup())
        .extracting(LayoutLookup::multiSelect, LayoutLookup::assetType)
        .containsExactly(null, "model");
    assertThat(subAgentIds.lookup())
        .extracting(LayoutLookup::multiSelect, LayoutLookup::assetType)
        .containsExactly(Boolean.TRUE, "agent");
  }

  @Test
  void shouldUseEnumBackedOptionsForStringSelectFields() {
    final BuilderDefinition agentDefinition = BuilderDefinitionUtils.generate("agent", BaseAgentConfig.class);
    final BuilderDefinition modelDefinition = BuilderDefinitionUtils.generate("model", ModelConfig.class);

    final LayoutField agentType = agentDefinition.ui().fields().get("/type");
    final LayoutField modelType = modelDefinition.ui().fields().get("/type");
    final Map<String, Object> modelSchema =
        CollectionUtils.getMapFromMap(modelDefinition.schema(), "properties");
    final List<?> modelSchemaOptions =
        CollectionUtils.getValueFromMap(
            CollectionUtils.getMapFromMap(modelSchema, "type"), "enum", List.class);

    assertThat(agentType.options()).isEqualTo(List.of("default", "orchestrator"));
    assertThat(modelType.options()).isEqualTo(List.of("ollama", "open_ai_compatible", "gemini"));
    assertThat(modelSchemaOptions).isEqualTo(List.of("ollama", "open_ai_compatible", "gemini"));
  }

  @Test
  void shouldBuildExpectedUiForModelConfig() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("model", ModelConfig.class);

    assertThat(definition.ui().presets())
        .extracting(LayoutPreset::id)
        .containsExactly("balanced", "focused", "creative");

    assertThat(definition.ui().fields()).containsKeys(
        "/type", "/model", "/apiKey", "/serverArgs", "/toolCallingEnabled", "/temperature");
    assertThat(definition.ui().fields().get("/type"))
        .extracting(LayoutField::label, LayoutField::widget, LayoutField::step, LayoutField::section)
        .containsExactly("Provider Type", "SELECT", "identity", "identity");
    assertThat(definition.ui().fields().get("/type").options())
        .isEqualTo(List.of("ollama", "open_ai_compatible", "gemini"));
    assertThat(definition.ui().fields().get("/apiKey").sensitive()).isTrue();
    assertThat(definition.ui().fields().get("/serverArgs"))
        .extracting(LayoutField::widget, LayoutField::multiline, LayoutField::rows, LayoutField::collection)
        .containsExactly("TEXTAREA", Boolean.TRUE, 3, Boolean.TRUE);
    assertThat(definition.ui().fields().get("/toolCallingEnabled").widget()).isEqualTo("SWITCH");
    assertThat(definition.ui().fields().get("/temperature").numberType()).isEqualTo("decimal");
  }

  @Test
  void shouldInferOptionsForEnumSelectFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate("agent", OrchestratorAgentConfig.class);

    final LayoutField orchestrationMode = definition.ui().fields().get("/orchestrationMode");

    assertThat(orchestrationMode.widget()).isEqualTo("SELECT");
    assertThat(orchestrationMode.options())
        .isEqualTo(List.of("UNKNOWN", "TRANSFER", "SEQUENTIAL", "PARALLEL"));
  }

  @Test
  void shouldExposeBuilderRulesForConditionalFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate("agent", OrchestratorAgentConfig.class);

    final LayoutField parallel = definition.ui().fields().get("/parallel");
    final LayoutField subAgentIds = definition.ui().fields().get("/subAgentIds");

    assertThat(parallel.rules()).hasSize(1);
    assertThat(parallel.rules().getFirst().effect()).isEqualTo("VISIBLE");
    final List<?> parallelArgs = (List<?>) parallel.rules().getFirst().expr().get("===");
    assertThat(parallelArgs.get(0)).isEqualTo(Map.of("var", "orchestrationMode"));
    assertThat(parallelArgs.get(1)).isEqualTo("PARALLEL");

    assertThat(subAgentIds.rules()).hasSize(1);
    final List<?> subAgentArgs = (List<?>) subAgentIds.rules().getFirst().expr().get("in");
    assertThat(subAgentArgs.get(0)).isEqualTo(Map.of("var", "type"));
    assertThat(subAgentArgs.get(1)).isEqualTo(List.of("orchestrator"));
  }

  @Test
  void shouldExposeNestedRulesForParallelConfig() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate("agent", OrchestratorParallelConfig.class);

    final LayoutField quorum = definition.ui().fields().get("/quorum");

    assertThat(quorum.rules()).hasSize(1);
    final List<?> quorumArgs = (List<?>) quorum.rules().getFirst().expr().get("===");
    assertThat(quorumArgs.get(0)).isEqualTo(Map.of("var", "stoppingPolicy"));
    assertThat(quorumArgs.get(1)).isEqualTo("QUORUM");
  }

  @Test
  void shouldExposeGuardrailRulesForConditionalFields() {
    final BuilderDefinition definition =
        BuilderDefinitionUtils.generate("guardrail", OutputRelevanceGuardrailRule.class);

    final LayoutField maxSteeringRetries = definition.ui().fields().get("/maxSteeringRetries");
    final LayoutField recency = definition.ui().fields().get("/recency");

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
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("model", ModelConfig.class);

    final LayoutField baseUrl = definition.ui().fields().get("/baseUrl");
    final LayoutField apiKey = definition.ui().fields().get("/apiKey");
    final LayoutField serverCommand = definition.ui().fields().get("/serverCommand");
    final LayoutField serverArgs = definition.ui().fields().get("/serverArgs");
    final LayoutField serverWorkdir = definition.ui().fields().get("/serverWorkdir");

    assertThat(baseUrl.rules()).hasSize(1);
    final List<?> baseUrlArgs = (List<?>) baseUrl.rules().getFirst().expr().get("in");
    assertThat(baseUrlArgs.get(0)).isEqualTo(Map.of("var", "type"));
    assertThat(baseUrlArgs.get(1)).isEqualTo(List.of("ollama", "open_ai_compatible"));

    assertThat(apiKey.rules()).hasSize(1);
    final List<?> apiKeyArgs = (List<?>) apiKey.rules().getFirst().expr().get("===");
    assertThat(apiKeyArgs.get(0)).isEqualTo(Map.of("var", "type"));
    assertThat(apiKeyArgs.get(1)).isEqualTo("gemini");

    assertThat(serverCommand.rules()).hasSize(1);
    assertThat(serverArgs.rules()).hasSize(1);
    assertThat(serverWorkdir.rules()).hasSize(1);
    assertThat(((List<?>) serverCommand.rules().getFirst().expr().get("===")).get(1))
        .isEqualTo("open_ai_compatible");
    assertThat(((List<?>) serverArgs.rules().getFirst().expr().get("===")).get(1))
        .isEqualTo("open_ai_compatible");
    assertThat(((List<?>) serverWorkdir.rules().getFirst().expr().get("===")).get(1))
        .isEqualTo("open_ai_compatible");
  }

  @Test
  void shouldExposeDynamicSchemaWidgetForDynamicSchemaFields() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("tools", ToolsConfig.class);

    final LayoutField configs = definition.ui().fields().get("/configs");

    assertThat(configs.widget()).isEqualTo("DYNAMIC_SCHEMA");
    assertThat(configs.dynamicSchema()).isNotNull();
    assertThat(configs.dynamicSchema().url()).isEqualTo("/schemas");
    assertThat(configs.dynamicSchema().method()).isEqualTo("POST");
    assertThat(configs.dynamicSchema().body()).containsEntry("assetType", "tool_configs");
    assertThat(configs.dynamicSchema().body()).containsEntry("assetId", "$item.toolName");
    assertThat(configs.dynamicSchema().body()).containsEntry("contextId", "$.id");
  }

  @Test
  void shouldOmitWidgetForSchemaDrivenFields() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("agent", BaseAgentConfig.class);

    assertThat(definition.ui().fields().get("/tools").widget()).isNull();
    assertThat(definition.ui().fields().get("/contextStrategy").widget()).isNull();
    assertThat(definition.ui().fields().get("/subAgentIds").widget()).isEqualTo("LOOKUP");
  }

  @Test
  void shouldMarkCollectionFieldsAsCollection() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("agent", BaseAgentConfig.class);

    assertThat(definition.ui().fields().get("/subAgentIds").collection()).isTrue();
    assertThat(definition.ui().fields().get("/modelId").collection()).isNull();
  }

  @Test
  void shouldMarkSensitiveFieldsFromSecureAnnotation() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("agent", BaseAgentConfig.class);

    assertThat(definition.ui().fields().get("/systemPrompt").sensitive()).isTrue();
    assertThat(definition.ui().fields().get("/modelId").sensitive()).isNull();
  }

  @Test
  void shouldExposeTextareaAttributesForMultilineFields() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("agent", BaseAgentConfig.class);

    final LayoutField systemPrompt = definition.ui().fields().get("/systemPrompt");

    assertThat(systemPrompt.widget()).isEqualTo("TEXTAREA");
    assertThat(systemPrompt.multiline()).isTrue();
    assertThat(systemPrompt.rows()).isEqualTo(6);
  }

  @Test
  void shouldIndexPresetsByJsonPathForRootAndNestedTypes() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate("nested", NestedPresetRoot.class);

    assertThat(definition.ui().presets())
        .extracting(LayoutPreset::id)
        .containsExactly("root-preset");
    assertThat(definition.ui().fields().get("/nested").presets())
        .extracting(LayoutPreset::id)
        .containsExactly("nested-preset");
  }

  @Test
  void shouldGenerateBuilderUiExampleJsonSnapshots() throws IOException {
    writeExampleJson("agent.json", BuilderDefinitionUtils.generate("agent", BaseAgentConfig.class));
    writeExampleJson("model.json", BuilderDefinitionUtils.generate("model", ModelConfig.class));
  }

  private static void writeExampleJson(final String fileName, final BuilderDefinition definition)
      throws IOException {
    final Path repoRoot = Path.of(System.getProperty("user.dir")).resolve("..").normalize();
    final Path path = repoRoot.resolve("docs/examples").resolve(fileName);
    Files.createDirectories(path.getParent());
    final String json = JsonUtils.toStableJson(definition);
    Files.writeString(path, json);

    assertThat(Files.readString(path)).isEqualTo(json);
  }

  @UiPreset(id = "root-preset", label = "Root Preset", preset = "{\"nested\":{\"value\":\"root\"}}")
  private static final class NestedPresetRoot {
    @com.agentengine.util.builder.annotations.UiField
    private NestedPresetChild nested;
  }

  @UiPreset(id = "nested-preset", label = "Nested Preset", preset = "{\"value\":\"child\"}")
  private static final class NestedPresetChild {
    @com.agentengine.util.builder.annotations.UiText
    private String value;
  }
}
