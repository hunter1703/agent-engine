package com.localagent.engine.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ToolsConfig implements Config {
    private List<String> enabled;
    private Map<String, Object> configs = new HashMap<>();

    public List<String> getEnabled() {
        return enabled == null ? null : new ArrayList<>(enabled);
    }

    public void setEnabled(List<String> enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, Object> configs) {
        this.configs = configs == null ? new HashMap<>() : new HashMap<>(configs);
    }
}
