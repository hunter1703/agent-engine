package com.agentengine.engine.tools;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.ServiceUtils;
import com.agentengine.engine.plugin.tools.ToolProvider;
import com.agentengine.engine.plugin.tools.ToolsetProvider;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public final class ToolServiceImpl implements ToolService {
  private final Map<String, ToolEntry> toolNameVsEntry;
  private final Map<String, ToolsetEntry> toolsetNameVsEntry;
  private final List<ToolDescriptor> allTools;

  @Inject
  public ToolServiceImpl(final @Any Instance<ToolProvider> providers, final @Any Instance<ToolsetProvider> toolsets,
      final DiscoveredToolProviders discoveredToolProviders) {
    final List<ToolProvider> toolProviders = CollectionUtils.nullSafeMutableList(ServiceUtils.loadServices(providers, ToolProvider.class));
    toolProviders.addAll(discoveredToolProviders.providers());
    final List<ToolsetProvider> toolsetProviders = ServiceUtils.loadServices(toolsets, ToolsetProvider.class);
    final Map<String, ToolDescriptor> nameVsDescriptor = getNameVsDescriptor(toolProviders, toolsetProviders);
    this.toolNameVsEntry = toolProviders.stream().filter(toolProvider -> toolProvider.descriptor() != null)
        .filter(toolProvider -> StringUtils.isNotBlank(toolProvider.descriptor().name())).collect(Collectors.toMap(
            toolProvider -> toolProvider.descriptor().name(), provider -> new ToolEntry(provider.descriptor(), provider), (a, b) -> a));
    this.toolsetNameVsEntry = toolsetProviders.stream().filter(toolsetProvider -> toolsetProvider.descriptor() != null)
        .filter(toolsetProvider -> StringUtils.isNotBlank(toolsetProvider.descriptor().name()))
        .collect(Collectors.toMap(toolsetProvider -> toolsetProvider.descriptor().name(),
            provider -> new ToolsetEntry(provider.descriptor(), provider), (a, b) -> a));
    this.allTools = List.copyOf(nameVsDescriptor.values());
  }

  @Override
  public List<ToolDescriptor> getTools() {
    return allTools;
  }

  @Override
  public ToolDescriptor getToolByName(final String toolName) {
    if (StringUtils.isBlank(toolName)) {
      return null;
    }
    final ToolEntry toolEntry = toolNameVsEntry.get(toolName);
    if (toolEntry != null) {
      return toolEntry.descriptor();
    }
    final ToolsetEntry toolsetEntry = toolsetNameVsEntry.get(toolName);
    return toolsetEntry == null ? null : toolsetEntry.descriptor();
  }

  @Override
  public ToolProvider getToolProvider(final String toolName) {
    if (StringUtils.isBlank(toolName) || !toolNameVsEntry.containsKey(toolName)) {
      return null;
    }
    return toolNameVsEntry.get(toolName).provider();
  }

  @Override
  public ToolsetProvider getToolsetProvider(final String toolsetName) {
    if (StringUtils.isBlank(toolsetName) || !toolsetNameVsEntry.containsKey(toolsetName)) {
      return null;
    }
    return toolsetNameVsEntry.get(toolsetName).provider();
  }

  private static Map<String, ToolDescriptor> getNameVsDescriptor(final List<ToolProvider> toolProviders,
      final List<ToolsetProvider> toolsetProviders) {
    final Map<String, ToolDescriptor> nameVsDescriptor = new LinkedHashMap<>();
    for (final ToolProvider toolProvider : toolProviders) {
      final ToolDescriptor descriptor = toolProvider.descriptor();
      if (descriptor == null || StringUtils.isBlank(descriptor.name())) {
        continue;
      }
      nameVsDescriptor.putIfAbsent(descriptor.name(), descriptor);
    }
    for (final ToolsetProvider toolsetProvider : toolsetProviders) {
      final ToolDescriptor descriptor = toolsetProvider.descriptor();
      if (descriptor == null || StringUtils.isBlank(descriptor.name())) {
        continue;
      }
      nameVsDescriptor.putIfAbsent(descriptor.name(), descriptor);
    }
    return nameVsDescriptor;
  }

  private record ToolEntry(ToolDescriptor descriptor, ToolProvider provider) {
  }

  private record ToolsetEntry(ToolDescriptor descriptor, ToolsetProvider provider) {
  }
}
