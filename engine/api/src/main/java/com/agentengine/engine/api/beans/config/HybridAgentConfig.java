package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "hybrid")
@BsonDiscriminator(value = "hybrid")
public final class HybridAgentConfig extends EngineConfig {
  private String toolAssistantModelId;

  public HybridAgentConfig() {
    super(EngineType.HYBRID);
  }

  public String getToolAssistantModelId() {
    return toolAssistantModelId;
  }

  public void setToolAssistantModelId(final String toolAssistantModelId) {
    this.toolAssistantModelId = toolAssistantModelId;
  }

  @Override
  public void validate() {
    validateBase();
    if (toolAssistantModelId == null || toolAssistantModelId.isBlank()) {
      throw new IllegalArgumentException("engine.toolAssistantModelId is required");
    }
  }
}
