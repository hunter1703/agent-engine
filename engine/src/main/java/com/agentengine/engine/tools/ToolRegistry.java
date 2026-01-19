package com.agentengine.engine.tools;

import com.agentengine.engine.beans.config.ToolsConfig;
import com.agentengine.engine.plugins.PluginLoader;
import com.agentengine.engine.utils.CollectionUtils;
import com.agentengine.engine.utils.StringUtils;

import java.util.*;
import java.util.logging.Logger;

public final class ToolRegistry {
  private static final Logger LOGGER = Logger.getLogger(ToolRegistry.class.getName());
  private ToolRegistry() {
  }

  public static List<Tool> loadTools(final String agentName, final ToolsConfig toolsConfig) {
    if (toolsConfig == null) {
      return Collections.emptyList();
    }
    final List<Tool> tools = new ArrayList<>();
    final ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class, PluginLoader.getClassLoader());
    final List<String> enabled = CollectionUtils.isEmpty(toolsConfig.getEnabled())
        ? List.of("ALL")
        : toolsConfig.getEnabled();
    final Map<String, Map<String, Object>> toolConfigs = toolsConfig.getConfigs();
    for (ToolProvider provider : loader) {
      LOGGER.info(STR."Found provider : \{provider.getClass().getName()} for agent : \{provider.agentName()} for tool : \{provider.toolName()}");
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
      final Tool tool = provider.create(toolConfig);
      if (tool == null) {
        continue;
      }
      tools.add(tool);
    }
    return tools;
  }
}
