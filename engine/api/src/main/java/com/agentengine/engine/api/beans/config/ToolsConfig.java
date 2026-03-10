package com.agentengine.engine.api.beans.config;

import com.agentengine.util.builder.annotations.UiDynamicSchema;
import com.agentengine.util.builder.annotations.UiField;
import com.agentengine.util.builder.annotations.UiText;
import java.util.HashMap;
import java.util.Map;

public final class ToolsConfig {

  @UiField(label = "Tool Name", order = 10)
  @UiText
  private String toolName;

  @UiField(label = "Configuration", order = 20)
  @UiDynamicSchema(assetType = "tool_configs", assetIdExpr = "$item.toolName", contextIdExpr = "$.id")
  private Map<String, Object> configs;

  public ToolsConfig() {}

  public ToolsConfig(final String toolName, final Map<String, Object> configs) {
    this.toolName = toolName;
    this.configs = configs;
  }

  public String getToolName() {
    return toolName;
  }

  public void setToolName(final String toolName) {
    this.toolName = toolName;
  }

  public Map<String, Object> getConfigs() {
    return configs;
  }

  public void setConfigs(final Map<String, Object> configs) {
    this.configs = configs == null ? new HashMap<>() : new HashMap<>(configs);
  }
}
