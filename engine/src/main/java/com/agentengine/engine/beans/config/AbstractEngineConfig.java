package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(
    typeKey = "type",
    seeAlso = {HybridEngineConfig.class, RouterEngineConfig.class})
public abstract class AbstractEngineConfig implements Config {
  private String type;

  @JSONField(name = "invocation_limit")
  private Integer invocationLimit;

  @JSONField(name = "tool_retry_limit")
  private Integer toolRetryLimit = 2;

  private String prompt;
  private String reasoning;

  protected AbstractEngineConfig(final EngineType engineType) {
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

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(final String prompt) {
    this.prompt = prompt;
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
    if (prompt == null || prompt.isBlank()) {
      throw new IllegalArgumentException("engine.prompt is required");
    }
  }

  protected enum EngineType {
    HYBRID,
    ROUTER
  }
}
