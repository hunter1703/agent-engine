package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.services.ToolService;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.plugins.PluginLoader;
import com.agentengine.engine.utils.Cache;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.common.cache.CacheBuilder;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public final class ToolRegistry implements ToolService {

  private final Cache<String, Map<String, ToolEntry>> toolCache;

  @Inject
  public ToolRegistry(final @Any Instance<ToolProvider> providers) {
    final List<ToolProvider> allProviders = new ArrayList<>();
    for (ToolProvider provider : providers) {
      allProviders.add(provider);
    }

    final ClassLoader pluginLoader = PluginLoader.getClassLoader();
    if (pluginLoader != Thread.currentThread().getContextClassLoader()) {
      allProviders.addAll(loadToolProviders(pluginLoader));
    }

    final com.google.common.cache.Cache<String, Map<String, ToolEntry>> toolCacheGuava =
        CacheBuilder.newBuilder().maximumSize(1000).expireAfterAccess(1, TimeUnit.HOURS).build();
    this.toolCache = new Cache<>(toolCacheGuava, agentId -> {
      final Map<String, ToolEntry> nameVsEntry = new HashMap<>();
      for (final ToolProvider provider : allProviders) {
        if (provider == null) {
          continue;
        }
        for (final ToolDescriptor descriptor : CollectionUtils.nullSafeList(provider.tools())) {
          final String name = descriptor.name();
          if (StringUtils.isBlank(name) || !matchesAgent(descriptor, agentId)) {
            continue;
          }
          nameVsEntry.put(name, new ToolEntry(descriptor, provider));
        }
      }
      return nameVsEntry;
    });
  }

  public List<BaseTool> loadTools(
      final AgentContext agentContext, final List<ToolsConfig> toolsConfig) {
    if (CollectionUtils.isEmpty(toolsConfig)) {
      return Collections.emptyList();
    }

    final String agentId = agentContext == null ? null : agentContext.agentId();
    final String cacheKey = Objects.requireNonNullElse(agentId, Tool.ALL);
    final Map<String, ToolEntry> toolMap = toolCache.get(cacheKey);
    final List<BaseTool> tools = new ArrayList<>(toolsConfig.size());

    for (final ToolsConfig config : toolsConfig) {
      if (config == null) {
        continue;
      }
      final String name = config.getToolName();
      if (name == null || name.isBlank()) {
        continue;
      }
      final ToolEntry entry = toolMap.get(name);
      if (entry == null) {
        continue;
      }
      final Tool tool = entry.provider().create(agentContext, name, config.getConfigs());
      if (tool != null) {
        tools.add(FunctionTool.create(tool, "execute"));
      }
    }
    return tools;
  }

  @Override
  public List<ToolDescriptor> getAvailableTools(final String agentId) {
    final String cacheKey = Objects.requireNonNullElse(agentId, Tool.ALL);
    return toolCache.get(cacheKey).values().stream().map(ToolEntry::descriptor).toList();
  }

  @Override
  public ToolDescriptor getToolById(final String agentId, final String toolId) {
    if (toolId == null || toolId.isBlank()) {
      return null;
    }
    final String cacheKey = Objects.requireNonNullElse(agentId, Tool.ALL);
    final ToolEntry entry = CollectionUtils.getValueFromMap(toolCache.get(cacheKey), toolId);
    if (entry == null) {
      return null;
    }
    return entry.descriptor();
  }

  private static boolean matchesAgent(final ToolDescriptor descriptor, final String agentId) {
    if (descriptor == null) {
      return false;
    }
    final List<String> agentIds = descriptor.agentIds();
    if (CollectionUtils.isEmpty(agentIds)) {
      return true;
    }
    if (agentIds.contains(Tool.ALL)) {
      return true;
    }
    if (agentId == null || agentId.isBlank()) {
      return false;
    }
    return agentIds.contains(agentId);
  }

  private static List<ToolProvider> loadToolProviders(final ClassLoader classLoader) {
    final List<ToolProvider> providers = new ArrayList<>();
    final ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class, classLoader);
    for (ToolProvider provider : loader) {
      if (provider == null) {
        continue;
      }
      providers.add(provider);
    }
    return providers;
  }

  private record ToolEntry(ToolDescriptor descriptor, ToolProvider provider) {}
}
