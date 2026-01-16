package com.localagent.engine.tools;

import com.localagent.engine.beans.config.AgentConfig;
import com.localagent.engine.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class ToolRegistry {
    private ToolRegistry() {
    }

    public static List<AgentTool> loadTools(String agentName, List<String> enabled, Map<String, Map<String, Object>> toolConfigs, AgentConfig agentConfig) {
        List<AgentTool> tools = new ArrayList<>();
        ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class);
        for (ToolProvider provider : loader) {
            if (!provider.agentName().equalsIgnoreCase(agentName)) {
                continue;
            }
            String toolName = provider.toolName();
            if (StringUtils.isBlank(toolName)) {
                continue;
            }
            if (!enabled.contains("ALL") && !enabled.contains(toolName)) {
                continue;
            }
            Map<String, Object> toolConfig = toolConfigs == null ? null : toolConfigs.get(toolName);
            AgentTool tool = provider.create(toolConfig, agentConfig);
            if (tool == null) {
                continue;
            }
            tools.add(tool);
        }
        return tools;
    }
}
