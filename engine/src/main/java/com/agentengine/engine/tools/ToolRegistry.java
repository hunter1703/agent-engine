package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.plugins.PluginLoader;
import com.google.adk.tools.BaseTool;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.stream.Collectors;

@Singleton
public final class ToolRegistry {

  private static final String ALL = "ALL";
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

  public List<BaseTool> loadTools(final AgentContext agentContext, final List<ToolsConfig> toolsConfig) {
    if (CollectionUtils.isEmpty(toolsConfig)) {
      return Collections.emptyList();
    }
    final List<BaseTool> tools = new ArrayList<>();

    final String agentId = agentContext == null ? null : agentContext.agentId();

    final Map<String, ToolProvider> toolNameVsProvider = this.providers.stream().filter(provider -> ALL.equals(provider.agentId()) || Objects.equals(provider.agentId(), agentId)).collect(Collectors.toMap(ToolProvider::toolName, provider -> provider, (existing, _) -> existing));
    for (final ToolsConfig config : toolsConfig) {
      final String toolName = config.getToolName();
      final ToolProvider provider = toolNameVsProvider.get(toolName);
      final BaseTool tool = provider.create(agentContext, config.getConfigs());
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
