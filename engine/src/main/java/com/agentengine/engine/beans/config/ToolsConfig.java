package com.agentengine.engine.beans.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ToolsConfig implements Config {
  private List<String> enabled;
  private Map<String, Map<String, Object>> configs = new HashMap<>();

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
}
