package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.beans.NamedEntity;
import com.agentengine.engine.api.utils.StringUtils;
import java.util.List;
import java.util.Locale;

public class ModelConfig extends NamedEntity implements Config {

  public enum Provider {
    OLLAMA("ollama"), OPEN_AI_COMPATIBLE("open_ai_compatible"), GEMINI("gemini");

    private final String type;

    Provider(final String type) {
      this.type = type;
    }

    public String type() {
      return type;
    }

    public boolean matches(final String value) {
      return type.equals(normalizeType(value));
    }

    public static Provider fromType(final String value) {
      final String normalized = normalizeType(value);
      if (StringUtils.isBlank(normalized)) {
        throw new IllegalArgumentException("type is required");
      }
      for (final Provider provider : values()) {
        if (provider.type.equals(normalized)) {
          return provider;
        }
      }
      throw new IllegalArgumentException("Unsupported model provider: " + value);
    }
  }

  private String baseUrl;

  private String type;
  private String model;
  private Double temperature;

  private Integer topK;

  private Double topP;

  private Double repeatPenalty;

  private Integer numPredict;

  private Integer maxContextLength;

  private List<String> stopTokens;

  private String responseFormat;

  private String apiKey;

  private boolean toolCallingEnabled = false;
  private boolean toolCallingSupported;
  private String serverCommand;

  private List<String> serverArgs;

  private String serverWorkdir;
  private String instructions;

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

  public boolean isToolCallingSupported() {
    return toolCallingSupported;
  }

  public void setToolCallingSupported(final boolean toolCallingSupported) {
    this.toolCallingSupported = toolCallingSupported;
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

  @Override
  public void validate() {
    if (StringUtils.isBlank(type)) {
      throw new IllegalArgumentException("type is required");
    }
    Provider.fromType(type);
    if (StringUtils.isBlank(model)) {
      throw new IllegalArgumentException("model is required");
    }
    if (StringUtils.isBlank(getName())) {
      throw new IllegalArgumentException("name is required");
    }
  }

  private static String normalizeType(final String type) {
    if (type == null) {
      return null;
    }
    return type.trim().toLowerCase(Locale.ROOT);
  }
}
