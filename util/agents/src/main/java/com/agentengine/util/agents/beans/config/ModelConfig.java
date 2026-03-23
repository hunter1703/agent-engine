package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.Secure;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.builder.annotations.UiAccess;
import com.agentengine.util.common.builder.annotations.UiAccessLevel;
import com.agentengine.util.common.builder.annotations.UiBoolean;
import com.agentengine.util.common.builder.annotations.UiConditionOperator;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiGroup;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.agentengine.util.common.builder.annotations.UiPreset;
import com.agentengine.util.common.builder.annotations.UiRule;
import com.agentengine.util.common.builder.annotations.UiRuleEffect;
import com.agentengine.util.common.builder.annotations.UiSelect;
import com.agentengine.util.common.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Locale;

@UiGroup(step = "identity", section = "identity", order = 0)
@UiPreset(id = "balanced", label = "Balanced", description = "General purpose default profile.", isDefault = true, preset = "{\"inference\":{\"temperature\":0.7,\"topP\":0.95,\"repeatPenalty\":1.0},\"capabilities\":{\"toolCallingEnabled\":false}}")
@UiPreset(id = "focused", label = "Focused", description = "Lower randomness for deterministic answers.", preset = "{\"inference\":{\"temperature\":0.2,\"topP\":0.8,\"repeatPenalty\":1.1},\"capabilities\":{\"toolCallingEnabled\":true}}")
@UiPreset(id = "creative", label = "Creative", description = "Higher diversity and broader token exploration.", preset = "{\"inference\":{\"temperature\":1.0,\"topP\":1.0,\"repeatPenalty\":1.0},\"capabilities\":{\"toolCallingEnabled\":false}}")
public class ModelConfig extends NamedEntity implements Config {

  public enum Provider {
    UNKNOWN, OLLAMA, OPEN_AI_COMPATIBLE, GEMINI;

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
        return Provider.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }

  @UiField(label = "Provider Type", step = "identity", section = "identity", order = 20)
  @UiSelect(enumType = Provider.class)
  @NotBlank
  private String type;

  @UiField(label = "Model Identifier", step = "identity", section = "identity", order = 30)
  @UiText
  @NotBlank
  private String model;

  @UiField(label = "Base URL", step = "integration", section = "integration", order = 10)
  @UiText
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "type", operator = UiConditionOperator.IN, values = {"OLLAMA", "OPEN_AI_COMPATIBLE"})
  private String baseUrl;

  @UiField(label = "API Key", step = "integration", section = "integration", order = 20)
  @UiText
  @Secure
  @JsonIgnore
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "type", values = {"GEMINI"})
  private String apiKey;

  @UiField(label = "Server Command", step = "integration", section = "integration", order = 30)
  @UiText
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "type", values = {"OPEN_AI_COMPATIBLE"})
  private String serverCommand;

  @UiField(label = "Server Arguments", step = "integration", section = "integration", order = 40)
  @UiText(multiline = true, rows = 3)
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "type", values = {"OPEN_AI_COMPATIBLE"})
  private List<String> serverArgs;

  @UiField(label = "Server Working Directory", step = "integration", section = "integration", order = 50)
  @UiText
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "type", values = {"OPEN_AI_COMPATIBLE"})
  private String serverWorkdir;

  @UiField(label = "Model Instructions", step = "integration", section = "integration", order = 60)
  @UiText(multiline = true, rows = 4)
  private String instructions;

  @UiField(label = "Response Format", step = "integration", section = "integration", order = 70, advanced = true)
  @UiText
  private String responseFormat;

  @UiField(label = "Enable Tool Calling", step = "integration", section = "integration", order = 80)
  @UiBoolean
  private boolean toolCallingEnabled = false;

  @UiField(label = "Temperature", step = "sampling", section = "sampling", order = 10)
  @UiNumber
  private Double temperature;

  @UiField(label = "Max Tokens to Generate", step = "sampling", section = "sampling", order = 20)
  @UiNumber
  private Integer numPredict;

  @UiField(label = "Top-K", step = "sampling", section = "sampling", order = 30)
  @UiNumber
  private Integer topK;

  @UiField(label = "Top-P", step = "sampling", section = "sampling", order = 40)
  @UiNumber
  private Double topP;

  @UiField(label = "Repeat Penalty", step = "sampling", section = "sampling", order = 50)
  @UiNumber
  private Double repeatPenalty;

  @UiField(label = "Max Context Length", step = "sampling", section = "sampling", order = 60, advanced = true)
  @UiNumber
  private Integer maxContextLength;

  @UiField(label = "Stop Tokens", step = "sampling", section = "sampling", order = 70, advanced = true)
  @UiText
  private List<String> stopTokens;

  @Override
  @UiField(label = "ID", step = "identity", section = "identity", order = 0)
  @UiText
  @UiAccess(create = UiAccessLevel.HIDDEN, edit = UiAccessLevel.READ_ONLY, view = UiAccessLevel.READ_ONLY)
  public String getId() {
    return super.getId();
  }

  @Override
  @UiAccess(create = UiAccessLevel.HIDDEN, edit = UiAccessLevel.READ_ONLY, view = UiAccessLevel.READ_ONLY)
  public void setId(final String id) {
    super.setId(id);
  }

  @Override
  @UiField(label = "Name", step = "identity", section = "identity", order = 10)
  @UiText
  public String getName() {
    return super.getName();
  }

  @Override
  public void setName(final String name) {
    super.setName(name);
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = normalizeType(type);
  }

  public String getModel() {
    return model;
  }

  public void setModel(final String model) {
    this.model = model;
  }

  public Double getTemperature() {
    return temperature;
  }

  public void setTemperature(final Double temperature) {
    this.temperature = temperature;
  }

  public Integer getTopK() {
    return topK;
  }

  public void setTopK(final Integer topK) {
    this.topK = topK;
  }

  public Double getTopP() {
    return topP;
  }

  public void setTopP(final Double topP) {
    this.topP = topP;
  }

  public Double getRepeatPenalty() {
    return repeatPenalty;
  }

  public void setRepeatPenalty(final Double repeatPenalty) {
    this.repeatPenalty = repeatPenalty;
  }

  public Integer getNumPredict() {
    return numPredict;
  }

  public void setNumPredict(final Integer numPredict) {
    this.numPredict = numPredict;
  }

  public Integer getMaxContextLength() {
    return maxContextLength;
  }

  public void setMaxContextLength(final Integer maxContextLength) {
    this.maxContextLength = maxContextLength;
  }

  public List<String> getStopTokens() {
    return stopTokens;
  }

  public void setStopTokens(final List<String> stopTokens) {
    this.stopTokens = stopTokens;
  }

  public String getResponseFormat() {
    return responseFormat;
  }

  public void setResponseFormat(final String responseFormat) {
    this.responseFormat = responseFormat;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(final String apiKey) {
    this.apiKey = apiKey;
  }

  public boolean isToolCallingEnabled() {
    return toolCallingEnabled;
  }

  public void setToolCallingEnabled(final boolean toolCallingEnabled) {
    this.toolCallingEnabled = toolCallingEnabled;
  }

  public String getServerCommand() {
    return serverCommand;
  }

  public void setServerCommand(final String serverCommand) {
    this.serverCommand = serverCommand;
  }

  public List<String> getServerArgs() {
    return serverArgs;
  }

  public void setServerArgs(final List<String> serverArgs) {
    this.serverArgs = serverArgs;
  }

  public String getServerWorkdir() {
    return serverWorkdir;
  }

  public void setServerWorkdir(final String serverWorkdir) {
    this.serverWorkdir = serverWorkdir;
  }

  public String getInstructions() {
    return instructions;
  }

  public void setInstructions(final String instructions) {
    this.instructions = instructions;
  }

  private static String normalizeType(final String type) {
    if (type == null) {
      return null;
    }
    return type.trim();
  }
}
