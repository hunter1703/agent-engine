package com.agentengine.engine.tools;

import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ToolsConfig;
import com.agentengine.engine.plugins.PluginLoader;
import com.agentengine.engine.utils.CollectionUtils;
import com.agentengine.engine.utils.StringUtils;

import java.util.*;

public final class ToolRegistry {
    private ToolRegistry() {
    }

    public static List<AgentTool> loadTools(final String agentName, final ToolsConfig toolsConfig) {
        if (toolsConfig == null) {
            return Collections.emptyList();
        }
        final List<AgentTool> tools = new ArrayList<>();
        final ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class, PluginLoader.getClassLoader());
        final List<String> enabled = CollectionUtils.isEmpty(toolsConfig.getEnabled()) ? List.of("ALL") : toolsConfig.getEnabled();
        final Map<String, Map<String, Object>> toolConfigs = toolsConfig.getConfigs();
        for (ToolProvider provider : loader) {
            if (!Objects.equals(provider.agentName(), agentName)) {
                continue;
            }
            final String toolName = provider.toolName();
            if (StringUtils.isBlank(toolName)) {
                continue;
            }
            if (!enabled.contains("ALL") && !enabled.contains(toolName)) {
                continue;
            }
            final Map<String, Object> toolConfig = toolConfigs == null ? null : toolConfigs.get(toolName);
            final AgentTool tool = provider.create(toolConfig);
            if (tool == null) {
                continue;
            }
            tools.add(tool);
        }
        return tools;
    }
}
