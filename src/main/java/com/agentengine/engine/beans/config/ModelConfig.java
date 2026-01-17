package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONField;
import java.util.List;

public class ModelConfig implements Config {

  public enum Provider {
    OLLAMA,
    LLAMA_CPP,
    OPEN_AI
  }

  @JSONField(name = "base_url")
  private String baseUrl;

  private String provider;
  private String model;
  private Double temperature;

  @JSONField(name = "top_k")
  private Integer topK;

  @JSONField(name = "top_p")
  private Double topP;

  @JSONField(name = "repeat_penalty")
  private Double repeatPenalty;

  @JSONField(name = "num_predict")
  private Integer numPredict;

  @JSONField(name = "max_context_length")
  private int maxContextLength;

  @JSONField(name = "stop_tokens")
  private List<String> stopTokens;

  @JSONField(name = "response_format")
  private String responseFormat;

  @JSONField(name = "thoughts_start_tag")
  private String thoughtsStartTag;

  @JSONField(name = "thoughts_end_tag")
  private String thoughtsEndTag;

  @JSONField(name = "thoughts_enabled")
  private boolean thoughtsEnabled;

  @JSONField(name = "context_config")
  private ContextConfig contextConfig;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
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

  public ContextConfig getContextConfig() {
    return contextConfig;
  }

  public void setContextConfig(final ContextConfig contextConfig) {
    this.contextConfig = contextConfig;
  }
}

