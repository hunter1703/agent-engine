package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.beans.config.HybridAgentConfig;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeKey = "type", seeAlso = {HybridAgentConfig.class, RouterEngineConfig.class})
@BsonDiscriminator(key = "type")
public abstract class EngineConfig implements Config {
  private String type;

  private Integer invocationLimit;

  private Integer toolRetryLimit = 2;

  private String systemPrompt;
  private String reasoningModelId;

  protected EngineConfig(final EngineType engineType) {
    this.type = engineType.name().toLowerCase();
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public Integer getInvocationLimit() {
    return invocationLimit;
  }

  public void setInvocationLimit(final Integer invocationLimit) {
    this.invocationLimit = invocationLimit;
  }

  public Integer getToolRetryLimit() {
    return toolRetryLimit;
  }

  public void setToolRetryLimit(final Integer toolRetryLimit) {
    this.toolRetryLimit = toolRetryLimit;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(final String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public String getReasoningModelId() {
    return reasoningModelId;
  }

  public void setReasoningModelId(final String reasoningModelId) {
    this.reasoningModelId = reasoningModelId;
  }

  protected final void validateBase() {
    if (reasoningModelId == null || reasoningModelId.isBlank()) {
      throw new IllegalArgumentException("engine.reasoningModelId is required");
    }
    if (systemPrompt == null || systemPrompt.isBlank()) {
      throw new IllegalArgumentException("engine.systemPrompt is required");
    }
  }

  protected enum EngineType {
    HYBRID, ROUTER
  }
}
