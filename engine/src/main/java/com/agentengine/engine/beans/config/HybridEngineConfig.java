package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(typeName = "hybrid")
public final class HybridEngineConfig extends AbstractEngineConfig {
  private String tool;

  public HybridEngineConfig() {
    super(EngineType.HYBRID);
  }

  public String getTool() {
    return tool;
  }

  public void setTool(final String tool) {
    this.tool = tool;
  }

  @Override
  public void validate() {
    validateBase();
    if (tool == null || tool.isBlank()) {
      throw new IllegalArgumentException("engine.tool is required");
    }
  }
}
