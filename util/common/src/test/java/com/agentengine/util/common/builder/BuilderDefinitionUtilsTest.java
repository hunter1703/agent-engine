package com.agentengine.util.common.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.Secure;
import com.agentengine.util.common.builder.annotations.UiAccess;
import com.agentengine.util.common.builder.annotations.UiAccessLevel;
import com.agentengine.util.common.builder.annotations.UiConditionOperator;
import com.agentengine.util.common.builder.annotations.UiDynamicSchema;
import com.agentengine.util.common.builder.annotations.UiExpression;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiLookup;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.agentengine.util.common.builder.annotations.UiPreset;
import com.agentengine.util.common.builder.annotations.UiRule;
import com.agentengine.util.common.builder.annotations.UiRuleEffect;
import com.agentengine.util.common.builder.annotations.UiSelect;
import com.agentengine.util.common.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuilderDefinitionUtilsTest {

  @Test
  void shouldOnlyExposeAnnotatedFieldsAndMetadata() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate(SampleConfig.class);

    assertThat(definition.layout().fields())
        .containsKeys(
            "/id",
            "/systemPrompt",
            "/mode",
            "/toolIds",
            "/configs",
            "/retries",
            "/nested",
            "/requiredViaNotNull",
            "/requiredViaAccess");
    assertThat(definition.layout().fields()).doesNotContainKey("/internalValue");

    assertThat(definition.layout().fields().get("/id").access())
        .extracting(LayoutAccessPolicy::create, LayoutAccessPolicy::edit, LayoutAccessPolicy::view)
        .containsExactly(UiAccessLevel.HIDDEN, UiAccessLevel.READ_ONLY, UiAccessLevel.READ_ONLY);

    assertThat(definition.layout().fields().get("/systemPrompt"))
        .extracting(
            LayoutField::widget, LayoutField::multiline, LayoutField::rows, LayoutField::sensitive)
        .containsExactly("TEXTAREA", Boolean.TRUE, 6, Boolean.TRUE);

    assertThat(definition.layout().fields().get("/mode"))
        .extracting(LayoutField::widget, LayoutField::options)
        .containsExactly("SELECT", List.of("FAST", "SAFE"));

    assertThat(definition.layout().fields().get("/toolIds"))
        .extracting(LayoutField::widget, LayoutField::collection)
        .containsExactly("LOOKUP", Boolean.TRUE);
    assertThat(definition.layout().fields().get("/toolIds").lookup())
        .extracting(LayoutLookup::assetType, LayoutLookup::multiSelect)
        .containsExactly("tool", Boolean.TRUE);

    assertThat(definition.layout().fields().get("/configs").widget()).isEqualTo("DYNAMIC_SCHEMA");
    assertThat(definition.layout().fields().get("/configs").dynamicSchema())
        .extracting(LayoutDynamicSchema::url, LayoutDynamicSchema::method)
        .containsExactly("/schemas", "POST");
    assertThat(definition.layout().fields().get("/configs").dynamicSchema().body())
        .containsEntry("assetType", "tool_configs")
        .containsEntry("assetId", "$item.toolName")
        .containsEntry("contextId", "$.id");
  }

  @Test
  void shouldMarkFieldsRequiredFromValidationAndAccessRules() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate(SampleConfig.class);

    assertThat(requiredFields(definition.schema()))
        .contains("systemPrompt", "requiredViaNotNull", "requiredViaAccess")
        .doesNotContain("mode", "toolIds", "internalValue");
  }

  @Test
  void shouldBuildRulesFromUiRuleAndUiExpression() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate(SampleConfig.class);
    final LayoutField retries = definition.layout().fields().get("/retries");

    assertThat(retries.rules()).hasSize(2);

    final LayoutFieldRule visible =
        retries.rules().stream()
            .filter(rule -> "VISIBLE".equals(rule.effect()))
            .findFirst()
            .orElseThrow();
    final LayoutFieldRule required =
        retries.rules().stream()
            .filter(rule -> "REQUIRED".equals(rule.effect()))
            .findFirst()
            .orElseThrow();

    final List<?> inArgs = (List<?>) visible.expr().get("in");
    assertThat(inArgs.get(0)).isEqualTo(Map.of("var", "mode"));
    assertThat(inArgs.get(1)).isEqualTo(List.of("FAST", "SAFE"));

    final List<?> eqArgs = (List<?>) required.expr().get("===");
    assertThat(eqArgs.get(0)).isEqualTo(Map.of("var", "mode"));
    assertThat(eqArgs.get(1)).isEqualTo("SAFE");
  }

  @Test
  void shouldAttachRootAndNestedPresets() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate(SampleConfig.class);

    assertThat(definition.layout().presets())
        .extracting(LayoutPreset::id)
        .containsExactly("root-preset");
    assertThat(definition.layout().fields().get("/nested").presets())
        .extracting(LayoutPreset::id)
        .containsExactly("nested-preset");
  }

  @Test
  void shouldAddAutoVisibilityRulesForPolymorphicSubtypeFields() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate(Animal.class);

    final LayoutField whiskers = definition.layout().fields().get("/whiskers");
    final LayoutField barkVolume = definition.layout().fields().get("/barkVolume");

    assertThat(whiskers.rules()).hasSize(1);
    assertThat(barkVolume.rules()).hasSize(1);

    final List<?> whiskersArgs = (List<?>) whiskers.rules().getFirst().expr().get("===");
    final List<?> barkArgs = (List<?>) barkVolume.rules().getFirst().expr().get("===");

    assertThat(whiskersArgs.get(0)).isEqualTo(Map.of("var", "type"));
    assertThat(whiskersArgs.get(1)).isEqualTo("CAT");

    assertThat(barkArgs.get(0)).isEqualTo(Map.of("var", "type"));
    assertThat(barkArgs.get(1)).isEqualTo("DOG");
  }

  @Test
  void shouldSanitizeReadOnlyAndHiddenFieldsPerMode() {
    final BuilderDefinition definition = BuilderDefinitionUtils.generate(SanitizeFixture.class);
    final SanitizeFixture payload = new SanitizeFixture();
    payload.id = "agent-1";
    payload.name = "Assistant";
    payload.readOnlyOnEdit = "locked";

    final SanitizeFixture sanitized =
        BuilderDefinitionUtils.sanitize(definition, BuilderMode.EDIT, payload);

    assertThat(sanitized.id).isNull();
    assertThat(sanitized.readOnlyOnEdit).isNull();
    assertThat(sanitized.name).isEqualTo("Assistant");
  }

  private static List<String> requiredFields(final Map<String, Object> schema) {
    @SuppressWarnings("unchecked")
    final List<Object> raw = CollectionUtils.getValueFromMap(schema, "required", List.class);
    if (raw == null) {
      return List.of();
    }
    return raw.stream().map(String::valueOf).toList();
  }

  @UiPreset(id = "root-preset", label = "Root", preset = "{\"mode\":\"FAST\"}")
  private static final class SampleConfig {

    @UiField private String id;

    @UiField
    @UiText(multiline = true, rows = 6)
    @Secure
    @NotBlank
    private String systemPrompt;

    @UiField
    @UiSelect(enumType = Mode.class)
    private String mode;

    @UiField
    @UiLookup(assetType = "tool")
    private List<String> toolIds;

    @UiField
    @UiDynamicSchema(
        assetType = "tool_configs",
        assetIdExpr = "$item.toolName",
        contextIdExpr = "$.id")
    private Map<String, Object> configs;

    @UiField
    @UiRule(
        effect = UiRuleEffect.VISIBLE,
        field = "mode",
        operator = UiConditionOperator.IN,
        values = {"FAST", "SAFE"})
    @UiExpression(effect = UiRuleEffect.REQUIRED, value = "{\"===\":[{\"var\":\"mode\"},\"SAFE\"]}")
    private Integer retries;

    @UiField private NestedConfig nested;

    @UiField @NotNull private Integer requiredViaNotNull;

    @UiField
    @UiAccess(create = UiAccessLevel.REQUIRED)
    private String requiredViaAccess;

    private String internalValue;
  }

  @UiPreset(id = "nested-preset", label = "Nested", preset = "{\"value\":\"preset\"}")
  private static final class NestedConfig {
    @UiText private String value;
  }

  private enum Mode {
    UNKNOWN,
    FAST,
    SAFE
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXISTING_PROPERTY,
      property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Cat.class, name = "CAT"),
    @JsonSubTypes.Type(value = Dog.class, name = "DOG")
  })
  private abstract static class Animal {
    @UiField
    @UiSelect(enumType = AnimalType.class)
    private String type;
  }

  private static final class Cat extends Animal {
    @UiField @UiText private String whiskers;
  }

  private static final class Dog extends Animal {
    @UiField @UiNumber private Integer barkVolume;
  }

  private enum AnimalType {
    UNKNOWN,
    CAT,
    DOG
  }

  public static final class SanitizeFixture {
    @UiField public String id;

    @UiField public String name;

    @UiField
    @UiAccess(edit = UiAccessLevel.READ_ONLY)
    public String readOnlyOnEdit;
  }
}
