package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "hybrid")
@BsonDiscriminator(value = "hybrid")
public final class HybridEngineConfig extends EngineConfig {
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
