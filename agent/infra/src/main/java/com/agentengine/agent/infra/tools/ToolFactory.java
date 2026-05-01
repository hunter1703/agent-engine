package com.agentengine.agent.infra.tools;

import com.agentengine.agent.infra.tools.knowledge.SearchKnowledgeTool;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.beans.config.ToolsConfig;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public final class ToolFactory {

    private final ToolService toolService;
    private final KnowledgeService knowledgeService;
    private final AgentService agentService;
    private final InfraConfigService infraConfigService;

    @Inject
    public ToolFactory(final ToolService toolService, final KnowledgeService knowledgeService, final AgentService agentService, final InfraConfigService infraConfigService) {
        this.toolService = toolService;
        this.knowledgeService = knowledgeService;
        this.agentService = agentService;
        this.infraConfigService = infraConfigService;
    }

    public HumanInTheLoopTool getHITLTool() {
        return new HumanInTheLoopTool();
    }

    public SearchKnowledgeTool getSearchKnowledgeTool() {
        return new SearchKnowledgeTool(knowledgeService, agentService, infraConfigService);
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
