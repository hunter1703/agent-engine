package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolSuite;
import com.agentengine.engine.api.beans.ToolEntity;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.services.ToolService;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.plugins.PluginLoader;
import com.google.adk.tools.BaseTool;
import com.agentengine.engine.utils.Cache;
import com.google.common.cache.CacheBuilder;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public final class ToolRegistry implements ToolService {

  private static final String ALL = "ALL";

  private final Cache<String, Map<String, ToolSuite>> suiteCache;

  private final Cache<String, Map<String, ToolProvider>> providerCache;

  @Inject
  public ToolRegistry(final @Any Instance<ToolProvider> providers, final @Any Instance<ToolSuite> suites) {
    final List<ToolProvider> allProviders = new ArrayList<>();
    final List<ToolSuite> allSuites = new ArrayList<>();

    for (ToolProvider provider : providers) {
      allProviders.add(provider);
    }
    for (ToolSuite suite : suites) {
      allSuites.add(suite);
    }

    final ClassLoader pluginLoader = PluginLoader.getClassLoader();
    if (pluginLoader != Thread.currentThread().getContextClassLoader()) {
      allProviders.addAll(loadToolProviders(pluginLoader));
      allSuites.addAll(loadToolSuites(pluginLoader));
    }

    final com.google.common.cache.Cache<String, Map<String, ToolProvider>> toolProvidersGuavaCache = CacheBuilder
        .newBuilder().maximumSize(1000).expireAfterAccess(1, TimeUnit.HOURS).build();
    this.providerCache = new com.agentengine.engine.utils.Cache<>(toolProvidersGuavaCache,
        key -> getProvidersMap(key, allProviders));

    final com.google.common.cache.Cache<String, Map<String, ToolSuite>> suitesGuavaCache = CacheBuilder.newBuilder()
        .maximumSize(1000).expireAfterAccess(1, TimeUnit.HOURS).build();
    this.suiteCache = new com.agentengine.engine.utils.Cache<>(suitesGuavaCache, key -> getSuitesMap(key, allSuites));
  }

  public List<BaseTool> loadTools(final AgentContext agentContext, final List<ToolsConfig> toolsConfig) {
    if (CollectionUtils.isEmpty(toolsConfig)) {
      return Collections.emptyList();
    }

    final String agentId = agentContext == null ? null : agentContext.agentId();

    final Map<String, ToolSuite> suiteMap = suiteCache.get(agentId);
    final Map<String, ToolProvider> providerMap = providerCache.get(agentId);

    final List<BaseTool> tools = new ArrayList<>(toolsConfig.size());

    for (final ToolsConfig config : toolsConfig) {
      final String name = config.getToolName();

      final ToolSuite suite = suiteMap.get(name);
      if (suite != null) {
        for (final ToolProvider provider : suite.toolProviders()) {
          final BaseTool tool = provider.create(agentContext, config.getConfigs());
          if (tool != null) {
            tools.add(tool);
          }
        }
        continue;
      }

      final ToolProvider provider = providerMap.get(name);
      if (provider != null) {
        final BaseTool tool = provider.create(agentContext, config.getConfigs());
        if (tool != null) {
          tools.add(tool);
        }
      }
    }
    return tools;
  }

  @Override
  public List<ToolEntity> getAvailableTools(String agentId) {
    return Stream
        .concat(
            providerCache.get(agentId).values().stream().filter(p -> !p.isSubTool())
                .map(p -> new ToolEntity(p.toolName(), p.toolName(), null)),
            suiteCache.get(agentId).values().stream().map(s -> new ToolEntity(s.suiteName(), s.suiteName(), null)))
        .toList();
  }

  private Map<String, ToolSuite> getSuitesMap(String agentId, List<ToolSuite> suites) {
    return suites.stream().filter(s -> ALL.equals(s.agentId()) || Objects.equals(s.agentId(), agentId))
        .collect(Collectors.toUnmodifiableMap(ToolSuite::suiteName, s -> s, (existing, _) -> existing));
  }

  private Map<String, ToolProvider> getProvidersMap(String agentId, List<ToolProvider> providers) {
    return providers.stream().filter(p -> ALL.equals(p.agentId()) || Objects.equals(p.agentId(), agentId))
        .collect(Collectors.toUnmodifiableMap(ToolProvider::toolName, p -> p, (existing, _) -> existing));
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

  private static List<ToolSuite> loadToolSuites(final ClassLoader classLoader) {
    final List<ToolSuite> suites = new ArrayList<>();
    final ServiceLoader<ToolSuite> suiteLoader = ServiceLoader.load(ToolSuite.class, classLoader);
    for (ToolSuite suite : suiteLoader) {
      if (suite == null) {
        continue;
      }
      suites.add(suite);
    }
    return suites;
  }
}
