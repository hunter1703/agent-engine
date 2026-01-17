package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(
    typeKey = "type",
    seeAlso = {HybridEngineConfig.class, RouterEngineConfig.class})
public abstract class EngineConfig implements Config {
  private String type;

  private Integer invocationLimit;

  private Integer toolRetryLimit = 2;

  private String systemPrompt;
  private String reasoning;

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

  public String getReasoning() {
    return reasoning;
  }

  public void setReasoning(final String reasoning) {
    this.reasoning = reasoning;
  }

  protected final void validateBase() {
    if (reasoning == null || reasoning.isBlank()) {
      throw new IllegalArgumentException("engine.reasoning is required");
    }
    if (systemPrompt == null || systemPrompt.isBlank()) {
      throw new IllegalArgumentException("engine.systemPrompt is required");
    }
  }

  protected enum EngineType {
    HYBRID,
    ROUTER
  }
}
