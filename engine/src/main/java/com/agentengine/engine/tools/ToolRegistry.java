package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.ToolProvider;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.plugins.PluginLoader;
import com.google.adk.tools.BaseTool;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public final class ToolRegistry {

  private final List<ToolProvider> providers;

  @Inject
  public ToolRegistry(final Instance<ToolProvider> providers) {
    final List<ToolProvider> allProviders = new ArrayList<>();

    for (ToolProvider provider : providers) {
      allProviders.add(provider);
    }

    final ClassLoader pluginLoader = PluginLoader.getClassLoader();
    if (pluginLoader != Thread.currentThread().getContextClassLoader()) {
      loadProviders(pluginLoader, allProviders);
    }

    this.providers = Collections.unmodifiableList(allProviders);
  }

  public List<BaseTool> loadTools(final AgentContext agentContext, final ToolsConfig toolsConfig) {
    if (toolsConfig == null) {
      return Collections.emptyList();
    }
    final List<BaseTool> tools = new ArrayList<>();

    final String agentId = agentContext == null ? null : agentContext.agentId();

    final List<String> enabled = CollectionUtils.isEmpty(toolsConfig.getEnabled())
        ? List.of("ALL")
        : toolsConfig.getEnabled();
    final Map<String, Map<String, Object>> toolConfigs = toolsConfig.getConfigs();

    for (ToolProvider provider : this.providers) {
      if (!Objects.equals(provider.agentId(), agentId) && !"ALL".equals(provider.agentId())) {
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
      final BaseTool tool = provider.create(agentContext, toolConfig);
      if (tool == null) {
        continue;
      }
      tools.add(tool);
    }
    return tools;
  }

  private static void loadProviders(final ClassLoader classLoader, final List<ToolProvider> providers) {
    if (classLoader == null) {
      return;
    }
    final ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class, classLoader);
    for (ToolProvider provider : loader) {
      if (provider == null) {
        continue;
      }
      providers.add(provider);
    }
  }
}
