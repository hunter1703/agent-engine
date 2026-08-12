package com.agentengine.agent.infra.tools;

import com.agentengine.agent.infra.ServiceUtils;
import com.agentengine.agent.infra.tools.knowledge.SearchKnowledgeTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.SchemaUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.tools.LoadArtifactsTool;
import io.vertx.json.schema.common.dsl.Schemas;
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
    private static final ToolDescriptor LOAD_ARTIFACT_TOOL_DESCRIPTOR = new ToolDescriptor(
            LoadArtifactsTool.INSTANCE.name(),
            LoadArtifactsTool.INSTANCE.description(),
            SchemaUtils.toMap(Schemas.objectSchema()
                    .property("artifact_names", Schemas.arraySchema().items(Schemas.stringSchema()))),
            null);
    private static final List<ToolDescriptor> STANDARD_TOOLS =
            List.of(SearchKnowledgeTool.DESCRIPTOR, LOAD_ARTIFACT_TOOL_DESCRIPTOR);
    private final Map<String, ToolEntry> toolNameVsEntry;
    private final Map<String, ToolsetEntry> toolsetNameVsEntry;
    private final List<ToolDescriptor> allTools;

    @Inject
    public ToolServiceImpl(
            final @Any Instance<ToolProvider> providers,
            final @Any Instance<ToolsetProvider> toolsets,
            final DiscoveredToolProviders discoveredToolProviders) {
        final List<ToolProvider> toolProviders =
                CollectionUtils.nullSafeMutableList(ServiceUtils.loadServices(providers, ToolProvider.class));
        toolProviders.addAll(discoveredToolProviders.providers());
        final List<ToolsetProvider> toolsetProviders = ServiceUtils.loadServices(toolsets, ToolsetProvider.class);
        this.toolNameVsEntry = toolProviders.stream()
                .filter(toolProvider -> toolProvider.descriptor() != null)
                .filter(toolProvider ->
                        StringUtils.isNotBlank(toolProvider.descriptor().name()))
                .collect(Collectors.toMap(
                        toolProvider -> toolProvider.descriptor().name(),
                        provider -> new ToolEntry(provider.descriptor(), provider),
                        (existingEntry, ignoredDuplicate) -> existingEntry));
        this.toolsetNameVsEntry = toolsetProviders.stream()
                .filter(toolsetProvider -> toolsetProvider.descriptor() != null)
                .filter(toolsetProvider ->
                        StringUtils.isNotBlank(toolsetProvider.descriptor().name()))
                .collect(Collectors.toMap(
                        toolsetProvider -> toolsetProvider.descriptor().name(),
                        provider -> new ToolsetEntry(provider.descriptor(), provider),
                        (existingEntry, ignoredDuplicate) -> existingEntry));
        this.allTools = List.copyOf(buildAllDescriptors(toolProviders, toolsetProviders));
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
    public List<ToolDescriptor> getStandardTools() {
        return STANDARD_TOOLS;
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

    private static List<ToolDescriptor> buildAllDescriptors(
            final List<ToolProvider> toolProviders, final List<ToolsetProvider> toolsetProviders) {
        final Map<String, ToolDescriptor> nameVsDescriptor = new LinkedHashMap<>();
        for (final ToolProvider toolProvider : toolProviders) {
            final ToolDescriptor descriptor = toolProvider.descriptor();
            if (descriptor != null && StringUtils.isNotBlank(descriptor.name())) {
                nameVsDescriptor.putIfAbsent(descriptor.name(), descriptor);
            }
        }
        for (final ToolsetProvider toolsetProvider : toolsetProviders) {
            final ToolDescriptor descriptor = toolsetProvider.descriptor();
            if (descriptor != null && StringUtils.isNotBlank(descriptor.name())) {
                nameVsDescriptor.putIfAbsent(descriptor.name(), descriptor);
            }
        }
        return List.copyOf(nameVsDescriptor.values());
    }

    private record ToolEntry(ToolDescriptor descriptor, ToolProvider provider) {}

    private record ToolsetEntry(ToolDescriptor descriptor, ToolsetProvider provider) {}
}
