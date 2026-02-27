package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.services.ToolService;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolSuite;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.plugins.PluginLoader;
import com.agentengine.engine.utils.Cache;
import com.google.adk.tools.BaseTool;
import com.google.common.cache.CacheBuilder;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
public final class ToolRegistry implements ToolService {

  private static final Logger LOG = LoggerFactory.getLogger(ToolRegistry.class);

  private final Cache<String, ToolCatalog> catalogCache;

  @Inject
  public ToolRegistry(
      final @Any Instance<ToolProvider> providers, final @Any Instance<ToolSuite> suites) {
    final ClassLoader pluginLoader = PluginLoader.getClassLoader();
    final List<ToolProvider> allProviders = collectProviders(providers, pluginLoader);
    final List<ToolSuite> allSuites = collectSuites(suites, allProviders, pluginLoader);

    final com.google.common.cache.Cache<String, ToolCatalog> toolCacheGuava =
        CacheBuilder.newBuilder().maximumSize(1000).expireAfterAccess(1, TimeUnit.HOURS).build();
    this.catalogCache =
        new Cache<>(toolCacheGuava, agentId -> buildCatalog(agentId, allProviders, allSuites));
  }

  public List<BaseTool> loadTools(
      final AgentContext agentContext, final List<ToolsConfig> toolsConfig) {
    final List<BaseTool> tools = new ArrayList<>();
    final String agentId = agentContext == null ? null : agentContext.agentId();
    final String cacheKey = Objects.requireNonNullElse(agentId, Tool.ALL);
    final ToolCatalog catalog = catalogCache.get(cacheKey);
 
    final List<String> catalogTools = new ArrayList<>(catalog.toolEntries().keySet());
    tools.add(SubmitFinalAnswerTool.INSTANCE);
 
    if (CollectionUtils.isEmpty(toolsConfig)) {
      final List<String> loadedToolNames = tools.stream().map(BaseTool::name).toList();
      LOG.info("loadTools - agentId={} catalogTools={} loadedTools={}", agentId, catalogTools, loadedToolNames);
      return tools;
    }
 
    for (final ToolsConfig config : toolsConfig) {
      final List<BaseTool> toolsForConfigs = getToolsForConfig(catalog, agentContext, config);
      if (CollectionUtils.isNotEmpty(toolsForConfigs)) {
        tools.addAll(toolsForConfigs);
      }
    }

    final List<String> loadedToolNames = tools.stream().map(BaseTool::name).toList();
    LOG.info("loadTools - agentId={} catalogTools={} loadedTools={}", agentId, catalogTools, loadedToolNames);
    return tools;
  }

  @Override
  public List<ToolDescriptor> getVisibleTools(final String agentId) {
    final String cacheKey = Objects.requireNonNullElse(agentId, Tool.ALL);
    return catalogCache.get(cacheKey).visibleList();
  }

  @Override
  public ToolDescriptor getToolById(final String agentId, final String toolId) {
    if (StringUtils.isBlank(toolId)) {
      return null;
    }
    final String cacheKey = Objects.requireNonNullElse(agentId, Tool.ALL);
    return catalogCache.get(cacheKey).visibleDescriptors().get(toolId);
  }

  private static List<ToolProvider> collectProviders(
      final Instance<ToolProvider> providers, final ClassLoader pluginLoader) {
    final List<ToolProvider> allProviders = new ArrayList<>();
    for (ToolProvider provider : providers) {
      if (provider != null) {
        allProviders.add(provider);
      }
    }
    if (pluginLoader != Thread.currentThread().getContextClassLoader()) {
      allProviders.addAll(loadToolProviders(pluginLoader));
    }
    return allProviders;
  }

  private static List<ToolSuite> collectSuites(
      final Instance<ToolSuite> suites,
      final List<ToolProvider> providers,
      final ClassLoader pluginLoader) {
    final Map<String, ToolSuite> nameVsSuite = new LinkedHashMap<>();
    for (ToolSuite suite : suites) {
      registerSuite(nameVsSuite, suite);
    }
    for (ToolProvider provider : CollectionUtils.nullSafeList(providers)) {
      if (provider instanceof ToolSuite suite) {
        registerSuite(nameVsSuite, suite);
      }
    }
    if (pluginLoader != Thread.currentThread().getContextClassLoader()) {
      ServiceLoader<ToolSuite> loader = ServiceLoader.load(ToolSuite.class, pluginLoader);
      for (ToolSuite suite : loader) {
        registerSuite(nameVsSuite, suite);
      }
    }
    return List.copyOf(nameVsSuite.values());
  }

  private static void registerSuite(final Map<String, ToolSuite> suites, final ToolSuite suite) {
    if (suite.descriptor() == null) {
      return;
    }
    final String name = suite.descriptor().name();
    if (StringUtils.isBlank(name)) {
      return;
    }
    suites.putIfAbsent(name, suite);
  }

  private static ToolCatalog buildCatalog(
      final String agentId, final List<ToolProvider> providers, final List<ToolSuite> suites) {
    final Map<String, ToolEntry> toolEntries = buildToolEntries(agentId, providers);
    final Map<String, SuiteEntry> suiteEntries = buildSuiteEntries(agentId, suites, toolEntries);
    final Set<String> suiteToolNames = collectSuiteToolNames(suiteEntries, toolEntries);
    final Map<String, ToolDescriptor> visibleDescriptors =
        buildVisibleDescriptors(toolEntries, suiteEntries, suiteToolNames);
    return new ToolCatalog(
        Collections.unmodifiableMap(toolEntries),
        Collections.unmodifiableMap(suiteEntries),
        Collections.unmodifiableMap(visibleDescriptors),
        List.copyOf(visibleDescriptors.values()));
  }

  private static Map<String, ToolEntry> buildToolEntries(
      final String agentId, final List<ToolProvider> providers) {
    final Map<String, ToolEntry> toolEntries = new LinkedHashMap<>();
    for (final ToolProvider provider : CollectionUtils.nullSafeList(providers)) {
      for (final ToolDescriptor descriptor : CollectionUtils.nullSafeList(provider.tools())) {
        if (descriptor == null || StringUtils.isBlank(descriptor.name())) {
          continue;
        }
        if (!matchesAgent(descriptor, agentId)) {
          continue;
        }
        toolEntries.putIfAbsent(descriptor.name(), new ToolEntry(descriptor, provider));
      }
    }
    return toolEntries;
  }

  private static Map<String, SuiteEntry> buildSuiteEntries(
      final String agentId,
      final List<ToolSuite> suites,
      final Map<String, ToolEntry> toolEntries) {
    final Map<String, SuiteEntry> suiteEntries = new LinkedHashMap<>();
    for (final ToolSuite suite : CollectionUtils.nullSafeList(suites)) {
      if (suite.descriptor() == null) {
        continue;
      }
      final ToolDescriptor descriptor = suite.descriptor();
      if (StringUtils.isBlank(descriptor.name()) || !matchesAgent(descriptor, agentId)) {
        continue;
      }
      suiteEntries.putIfAbsent(
          descriptor.name(), new SuiteEntry(descriptor, getAvailableTools(suite, toolEntries)));
    }
    return suiteEntries;
  }

  private static List<String> getAvailableTools(
      final ToolSuite suite, final Map<String, ToolEntry> toolEntries) {
    final String suiteName = suite.descriptor().name();
    final Set<String> names = new LinkedHashSet<>();
    for (final String name : CollectionUtils.nullSafeList(suite.toolNames())) {
      if (StringUtils.isBlank(name) || name.equals(suiteName)) {
        continue;
      }
      if (toolEntries.containsKey(name)) {
        names.add(name);
      }
    }
    return List.copyOf(names);
  }

  private static Set<String> collectSuiteToolNames(
      final Map<String, SuiteEntry> suiteEntries, final Map<String, ToolEntry> toolEntries) {
    final Set<String> names = new HashSet<>();
    for (final SuiteEntry entry : suiteEntries.values()) {
      for (final String name : entry.toolNames()) {
        if (toolEntries.containsKey(name)) {
          names.add(name);
        }
      }
    }
    return names;
  }

  private static Map<String, ToolDescriptor> buildVisibleDescriptors(
      final Map<String, ToolEntry> toolEntries,
      final Map<String, SuiteEntry> suiteEntries,
      final Set<String> suiteToolNames) {
    final Map<String, ToolDescriptor> visible = new LinkedHashMap<>();
    for (SuiteEntry entry : suiteEntries.values()) {
      visible.putIfAbsent(entry.descriptor().name(), entry.descriptor());
    }
    for (ToolEntry entry : toolEntries.values()) {
      if (suiteToolNames.contains(entry.descriptor().name())) {
        continue;
      }
      visible.putIfAbsent(entry.descriptor().name(), entry.descriptor());
    }
    return visible;
  }

  private static List<BaseTool> getToolsForConfig(
      final ToolCatalog catalog, final AgentContext agentContext, final ToolsConfig config) {
    if (config == null) {
      return Collections.emptyList();
    }
    final String name = config.getToolName();
    if (StringUtils.isBlank(name)) {
      return Collections.emptyList();
    }
    final SuiteEntry suiteEntry = catalog.suiteEntries().get(name);
    final List<BaseTool> tools = new ArrayList<>();
    if (suiteEntry != null) {
      for (final String toolName : suiteEntry.toolNames()) {
        final ToolEntry toolEntry = catalog.toolEntries().get(toolName);
        addIfPresent(tools, createTool(agentContext, toolEntry, config.getConfigs()));
      }
    } else {
      final ToolEntry toolEntry = catalog.toolEntries().get(name);
      addIfPresent(tools, createTool(agentContext, toolEntry, config.getConfigs()));
    }
    return tools;
  }

  private static BaseTool createTool(
      final AgentContext agentContext,
      final ToolEntry entry,
      final Map<String, Object> toolConfig) {
    if (entry == null) {
      return null;
    }
    return entry.provider().create(agentContext, entry.descriptor().name(), toolConfig);
  }

  private static void addIfPresent(final List<BaseTool> tools, final BaseTool tool) {
    if (tool != null) {
      tools.add(tool);
    }
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
    if (StringUtils.isBlank(agentId)) {
      return false;
    }
    return agentIds.contains(agentId);
  }

  private static List<ToolProvider> loadToolProviders(final ClassLoader classLoader) {
    final List<ToolProvider> providers = new ArrayList<>();
    final ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class, classLoader);
    for (ToolProvider provider : loader) {
      if (provider != null) {
        providers.add(provider);
      }
    }
    return providers;
  }

  private record ToolEntry(ToolDescriptor descriptor, ToolProvider provider) {}

  private record SuiteEntry(ToolDescriptor descriptor, List<String> toolNames) {}

  private record ToolCatalog(
      Map<String, ToolEntry> toolEntries,
      Map<String, SuiteEntry> suiteEntries,
      Map<String, ToolDescriptor> visibleDescriptors,
      List<ToolDescriptor> visibleList) {}
}
