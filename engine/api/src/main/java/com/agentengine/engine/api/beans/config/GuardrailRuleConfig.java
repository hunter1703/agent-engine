package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = InputRulesGuardrailRuleConfig.class, name = "INPUT_RULES"),
  @JsonSubTypes.Type(value = OutputRulesGuardrailRuleConfig.class, name = "OUTPUT_RULES"),
  @JsonSubTypes.Type(value = ToolRiskGuardrailRuleConfig.class, name = "TOOL_RISK"),
  @JsonSubTypes.Type(value = OnTopicGuardrailRuleConfig.class, name = "ON_TOPIC")
})
@BsonDiscriminator(key = "type")
public abstract class GuardrailRuleConfig {
  private String id;
  private String type;
  private GuardrailStage stage = GuardrailStage.UNKNOWN;
  private boolean enabled = true;
  private GuardrailAction action = GuardrailAction.WARN;
  private String message;

  protected GuardrailRuleConfig(final GuardrailRuleType type) {
    this.type = type.name();
  }

  protected GuardrailRuleConfig() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public GuardrailStage getStage() {
    return stage;
  }

  public void setStage(final GuardrailStage stage) {
    this.stage = stage == null ? GuardrailStage.UNKNOWN : stage;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public GuardrailAction getAction() {
    return action;
  }

  public void setAction(final GuardrailAction action) {
    this.action = action == null ? GuardrailAction.WARN : action;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }
}
