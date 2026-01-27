package com.agentengine.engine.api.beans.config;

import java.util.List;

public class ModelConfig implements Config {

  public enum Provider {
    OLLAMA, LLAMA_CPP, OPEN_AI
  }

  private String baseUrl;

  private String type;
  private String model;
  private Double temperature;

  private Integer topK;

  private Double topP;

  private Double repeatPenalty;

  private Integer numPredict;

  private int maxContextLength;

  private List<String> stopTokens;

  private String responseFormat;

  private String thoughtsStartTag;

  private String thoughtsEndTag;

  private boolean thoughtsEnabled;

  private boolean toolCallingEnabled = true;
  private boolean toolCallingSupported = true;

  private ContextManagerConfig contextManagerConfig = new LastNContextManagerConfig();

  private String serverCommand;

  private List<String> serverArgs;

  private String serverWorkdir;

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
    this.type = type;
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

  public int getMaxContextLength() {
    return maxContextLength;
  }

  public void setMaxContextLength(final int maxContextLength) {
    this.maxContextLength = maxContextLength;
  }

  public List<String> getStopTokens() {
    return stopTokens;
  }

  public void setStopTokens(final List<String> stopTokens) {
    this.stopTokens = stopTokens;
  }

  public String getThoughtsStartTag() {
    return thoughtsStartTag;
  }

  public void setThoughtsStartTag(final String thoughtsStartTag) {
    this.thoughtsStartTag = thoughtsStartTag;
  }

  public String getThoughtsEndTag() {
    return thoughtsEndTag;
  }

  public void setThoughtsEndTag(final String thoughtsEndTag) {
    this.thoughtsEndTag = thoughtsEndTag;
  }

  public String getResponseFormat() {
    return responseFormat;
  }

  public void setResponseFormat(final String responseFormat) {
    this.responseFormat = responseFormat;
  }

  public boolean isThoughtsEnabled() {
    return thoughtsEnabled;
  }

  public void setThoughtsEnabled(final boolean thoughtsEnabled) {
    this.thoughtsEnabled = thoughtsEnabled;
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

  public ContextManagerConfig getContextConfig() {
    return contextManagerConfig;
  }

  public void setContextConfig(final ContextManagerConfig contextManagerConfig) {
    this.contextManagerConfig = contextManagerConfig;
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
}
