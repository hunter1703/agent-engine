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

  private static final String ALL_TOOLS = "ALL";
  private static final String PLANNING_TOOL_KEY = "planning";
  private static final List<String> PLANNING_TOOL_NAMES = List.of("create_plan", "update_plan_info",
      "revise_current_plan", "update_subtask_state", "finish_plan", "view_current_plan");

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

    final Set<String> enabled = resolveEnabledTools(toolsConfig);
    final Map<String, Map<String, Object>> toolConfigs = toolsConfig.getConfigs();

    for (ToolProvider provider : this.providers) {
      if (!Objects.equals(provider.agentId(), agentId) && !"ALL".equals(provider.agentId())) {
        continue;
      }
      final String toolName = provider.toolName();
      if (StringUtils.isBlank(toolName)) {
        continue;
      }
      if (!enabled.contains(ALL_TOOLS) && !enabled.contains(toolName)) {
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

  private static Set<String> resolveEnabledTools(final ToolsConfig toolsConfig) {
    final List<String> enabledTools = CollectionUtils.nullSafeList(toolsConfig.getEnabled());
    final Set<String> resolved = new LinkedHashSet<>(enabledTools);
    if (resolved.isEmpty() && CollectionUtils.isEmpty(toolsConfig.getStandardTools())) {
      return resolved;
    }
    if (hasPlanningGroup(resolved) || hasPlanningGroup(toolsConfig.getStandardTools())) {
      resolved.addAll(PLANNING_TOOL_NAMES);
    }
    return resolved;
  }

  private static boolean hasPlanningGroup(final Collection<String> toolNames) {
    if (CollectionUtils.isEmpty(toolNames)) {
      return false;
    }
    for (final String toolName : toolNames) {
      if (PLANNING_TOOL_KEY.equals(toolName)) {
        return true;
      }
    }
    return false;
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
