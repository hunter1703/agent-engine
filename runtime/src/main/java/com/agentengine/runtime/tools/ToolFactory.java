package com.agentengine.runtime.tools;

import com.agentengine.runtime.session.SessionActorFactory;
import com.agentengine.util.agents.beans.config.ToolsConfig;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public final class ToolFactory {

    private final ToolService toolService;
    private final Instance<SessionActorFactory> sessionActorFactory;

    @Inject
    public ToolFactory(final ToolService toolService, final Instance<SessionActorFactory> sessionActorFactory) {
        this.toolService = toolService;
        this.sessionActorFactory = sessionActorFactory;
    }

    public HumanInTheLoopTool getHITLTool() {
        return new HumanInTheLoopTool(sessionActorFactory.get());
    }

    public List<BaseTool> buildTools(final List<ToolsConfig> toolConfigs) {
        if (CollectionUtils.isEmpty(toolConfigs)) {
            return List.of();
        }
        final List<BaseTool> tools = new ArrayList<>(toolConfigs.size());
        for (final ToolsConfig toolConfig : toolConfigs) {
            final ToolProvider provider = toolService.getToolProvider(toolConfig.getToolName());
            if (provider == null) {
                continue;
            }
            final BaseTool tool = provider.create(configMap(toolConfig));
            if (tool != null) {
                tools.add(tool);
            }
        }
        return tools.isEmpty() ? List.of() : List.copyOf(tools);
    }

    public List<BaseToolset> buildToolsets(final List<ToolsConfig> toolConfigs) {
        if (CollectionUtils.isEmpty(toolConfigs)) {
            return List.of();
        }
        final List<BaseToolset> toolsets = new ArrayList<>(toolConfigs.size());
        for (final ToolsConfig toolConfig : toolConfigs) {
            final ToolsetProvider provider = toolService.getToolsetProvider(toolConfig.getToolName());
            if (provider == null) {
                continue;
            }
            final BaseToolset toolset = provider.create(configMap(toolConfig));
            if (toolset != null) {
                toolsets.add(toolset);
            }
        }
        return toolsets.isEmpty() ? List.of() : List.copyOf(toolsets);
    }

    private static Map<String, Object> configMap(final ToolsConfig toolConfig) {
        return toolConfig == null ? Map.of() : CollectionUtils.nullSafeMap(toolConfig.getConfigs());
    }
}
