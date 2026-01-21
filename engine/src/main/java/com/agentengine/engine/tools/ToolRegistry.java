package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.plugins.PluginLoader;
import com.agentengine.commons.utils.CollectionUtils;
import com.agentengine.commons.utils.StringUtils;

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
    // Try to use context classloader first (useful for tests), fallback to PluginLoader classloader
    ClassLoader serviceClassLoader = Thread.currentThread().getContextClassLoader();
    if (serviceClassLoader.getResource("META-INF/services/com.agentengine.engine.tools.ToolProvider") == null) {
      serviceClassLoader = PluginLoader.getClassLoader();
    }
    final ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class, serviceClassLoader);
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
