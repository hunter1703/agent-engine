package com.agentengine.engine.tools;

import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class ToolRegistry {
  private ToolRegistry() {}

  public static List<AgentTool> loadTools(
      final String agentName,
      final List<String> enabled,
      final Map<String, Map<String, Object>> toolConfigs,
      final AgentConfig agentConfig) {
    final List<AgentTool> tools = new ArrayList<>();
    final ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class);
    for (ToolProvider provider : loader) {
      if (!provider.agentName().equalsIgnoreCase(agentName)) {
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
      final AgentTool tool = provider.create(toolConfig, agentConfig);
      if (tool == null) {
        continue;
      }
      tools.add(tool);
    }
    return tools;
  }
}

