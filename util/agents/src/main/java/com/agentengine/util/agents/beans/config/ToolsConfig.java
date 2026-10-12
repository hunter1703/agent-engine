package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.builder.annotations.UiDynamicSchema;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiLookup;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

public final class ToolsConfig {

  @UiField(label = "Tool Name", order = 10)
  @UiLookup(assetType = AssetClass.TOOL)
  @NotBlank
  private String toolName;

  @UiField(label = "Configuration", order = 20)
  @UiDynamicSchema(
      assetType = AssetClass.TOOL_CONFIGS,
      assetIdExpr = "$item.toolName",
      contextIdExpr = "$.id")
  private Map<String, Object> configs = new HashMap<>();

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
