package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.Secure;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.builder.annotations.UiAccess;
import com.agentengine.util.common.builder.annotations.UiAccessLevel;
import com.agentengine.util.common.builder.annotations.UiConditionOperator;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiRule;
import com.agentengine.util.common.builder.annotations.UiRuleEffect;
import com.agentengine.util.common.builder.annotations.UiSelect;
import com.agentengine.util.common.builder.annotations.UiStep;
import com.agentengine.util.common.builder.annotations.UiSteps;
import com.agentengine.util.common.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
    oneOf = {ChatModelConfig.class, EmbeddingModelConfig.class},
    discriminatorProperty = "type",
    discriminatorMapping = {
      @DiscriminatorMapping(value = "CHAT", schema = ChatModelConfig.class),
      @DiscriminatorMapping(value = "EMBEDDING", schema = EmbeddingModelConfig.class)
    })
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ChatModelConfig.class, name = "CHAT"),
  @JsonSubTypes.Type(value = EmbeddingModelConfig.class, name = "EMBEDDING")
})
@BsonDiscriminator
@UiSteps(
    steps = {
      @UiStep(id = "identity", label = "Identity", order = 0),
      @UiStep(id = "integration", label = "Integration", order = 1),
      @UiStep(id = "sampling", label = "Sampling Parameters", order = 2)
    })
public abstract class ModelConfig extends NamedEntity implements Config {

  public enum Provider {
    UNKNOWN,
    OLLAMA,
    OPEN_AI_COMPATIBLE,
    GEMINI;

    public String type() {
      return name();
    }

    public static Provider fromType(final String value) {
      final Provider provider = valueOfOrDefault(value);
      if (provider == UNKNOWN) {
        throw new IllegalArgumentException("Unsupported model provider: " + value);
      }
      return provider;
    }

    public static Provider valueOfOrDefault(final String value) {
      if (StringUtils.isBlank(value)) {
        return UNKNOWN;
      }
      try {
        return Provider.valueOf(value);
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }

  public enum ModelType {
    UNKNOWN,
    CHAT,
    EMBEDDING;

    public static ModelType valueOfOrDefault(final String value) {
      if (value == null || value.isBlank()) {
        return UNKNOWN;
      }
      try {
        return ModelType.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }

  @UiField(label = "Provider", step = "identity", order = 20)
  @UiSelect(enumType = Provider.class)
  @NotBlank
  private String provider;

  @UiField(label = "Model Type", step = "identity", order = 25)
  @UiSelect(enumType = ModelType.class)
  @NotBlank
  private String type;

  @UiField(label = "Model Identifier", step = "identity", order = 30)
  @UiText
  @NotBlank
  private String model;

  @UiField(label = "Base URL", step = "integration", order = 10)
  @UiText
  @UiRule(
      effect = UiRuleEffect.VISIBLE,
      field = "provider",
      operator = UiConditionOperator.IN,
      values = {"OLLAMA", "OPEN_AI_COMPATIBLE"})
  private String baseUrl;

  @UiField(label = "API Key", step = "integration", order = 20)
  @UiText
  @Secure
  @UiRule(
      effect = UiRuleEffect.VISIBLE,
      field = "provider",
      values = {"GEMINI", "OPEN_AI_COMPATIBLE"})
  private String apiKey;

  protected ModelConfig(final ModelType modelType) {
    this.type = modelType.name();
  }

  protected ModelConfig() {
    this(ModelType.UNKNOWN);
  }

  @Override
  @UiField(label = "ID", step = "identity", order = 0)
  @UiText
  @UiAccess(
      create = UiAccessLevel.HIDDEN,
      edit = UiAccessLevel.READ_ONLY,
      view = UiAccessLevel.READ_ONLY)
  public String getId() {
    return super.getId();
  }

  @Override
  @UiAccess(
      create = UiAccessLevel.HIDDEN,
      edit = UiAccessLevel.READ_ONLY,
      view = UiAccessLevel.READ_ONLY)
  public void setId(final String id) {
    super.setId(id);
  }

  @Override
  @UiField(label = "Name", step = "identity", order = 10)
  @UiText
  public String getName() {
    return super.getName();
  }

  @Override
  public void setName(final String name) {
    super.setName(name);
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(final String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(final String model) {
    this.model = model;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(final String apiKey) {
    this.apiKey = apiKey;
  }
}
