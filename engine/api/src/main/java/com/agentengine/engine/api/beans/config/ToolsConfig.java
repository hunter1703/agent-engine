package com.agentengine.engine.api.beans.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ToolsConfig {
  private List<String> enabled;
  private Map<String, Map<String, Object>> configs = new HashMap<>();
  private List<String> standardTools = new ArrayList<>();

  public List<String> getEnabled() {
    return enabled == null ? null : new ArrayList<>(enabled);
  }

  public void setEnabled(final List<String> enabled) {
    this.enabled = enabled;
  }

  public Map<String, Map<String, Object>> getConfigs() {
    return configs;
  }

  public void setConfigs(final Map<String, Map<String, Object>> configs) {
    this.configs = configs == null ? new HashMap<>() : new HashMap<>(configs);
  }

  public List<String> getStandardTools() {
    return standardTools;
  }

  public void setStandardTools(final List<String> standardTools) {
    this.standardTools = standardTools;
  }

  public void validate() {
  }
}
