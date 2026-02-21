package com.agentengine.engine.api.beans.config;

import java.util.HashMap;
import java.util.Map;

public final class ToolsConfig {
  private String toolName;
  private Map<String, Object> configs;

  public ToolsConfig() {
  }

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
