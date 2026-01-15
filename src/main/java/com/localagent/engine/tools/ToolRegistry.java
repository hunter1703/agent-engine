package com.localagent.engine.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class ToolRegistry {
    private ToolRegistry() {
    }

    public static List<AgentTool> loadTools(String agentName, List<String> enabled, Map<String, Object> toolConfigs, Map<String, Object> agentConfig) {
        List<AgentTool> tools = new ArrayList<>();
        ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class);
        for (ToolProvider provider : loader) {
            if (!provider.agentName().equalsIgnoreCase(agentName)) {
                continue;
            }
            String toolName = provider.toolName();
            if (toolName == null || toolName.isBlank()) {
                continue;
            }
            if (enabled != null && !enabled.isEmpty() && !enabled.contains(toolName)) {
                continue;
            }
            Object configValue = toolConfigs == null ? null : toolConfigs.get(toolName);
            Map<String, Object> toolConfig = configValue instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            AgentTool tool = provider.create(toolConfig, agentConfig);
            if (tool == null) {
                continue;
            }
            tools.add(tool);
        }
        return tools;
    }
}
