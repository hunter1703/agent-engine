package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.plugins.PluginLoader;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.logging.Logger;

@Singleton
public final class ToolRegistry {
  private static final Logger LOGGER = Logger.getLogger(ToolRegistry.class.getName());

  public List<Tool> loadTools(final String agentId, final ToolsConfig toolsConfig) {
        if (toolsConfig == null) {
            return Collections.emptyList();
        }
        final List<Tool> tools = new ArrayList<>();
        final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader pluginLoader = PluginLoader.getClassLoader();
        final Set<String> seenProviders = new HashSet<>();
        final List<ToolProvider> providers = new ArrayList<>();
        loadProviders(contextLoader, providers, seenProviders);
        if (pluginLoader != contextLoader) {
            loadProviders(pluginLoader, providers, seenProviders);
        }
        final List<String> enabled = CollectionUtils.isEmpty(toolsConfig.getEnabled())
                ? List.of("ALL")
                : toolsConfig.getEnabled();
        final Map<String, Map<String, Object>> toolConfigs = toolsConfig.getConfigs();
        for (ToolProvider provider : providers) {
            LOGGER.info(STR."Found provider : \{provider.getClass().getName()} for agent : \{provider.agentId()} for tool : \{provider.toolName()}");
            if (!Objects.equals(provider.agentId(), agentId)) {
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

  private static void loadProviders(final ClassLoader classLoader, final List<ToolProvider> providers,
      final Set<String> seenProviders) {
    if (classLoader == null) {
      return;
    }
    final ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class, classLoader);
    for (ToolProvider provider : loader) {
      if (provider == null) {
        continue;
      }
      final String name = provider.getClass().getName();
      if (!seenProviders.add(name)) {
        continue;
      }
      providers.add(provider);
    }
  }
}
