package com.agentengine.agent.infra.tools;

import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.tools.agent.RefreshRemindersTool;
import com.agentengine.agent.infra.tools.knowledge.SearchKnowledgeTool;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.knowledge.api.services.MediaService;
import com.agentengine.util.agents.beans.config.ToolsConfig;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public final class ToolFactory {

  private final ToolService toolService;
  private final FileService fileService;
  private final KnowledgeService knowledgeService;
  private final Provider<MediaService> mediaServiceProvider;
  private final ReminderSyncService reminderSyncService;
  private final ModelProvider modelProvider;

  @Inject
  public ToolFactory(
      final ToolService toolService,
      final FileService fileService,
      final KnowledgeService knowledgeService,
      final Provider<MediaService> mediaServiceProvider,
      final ReminderSyncService reminderSyncService,
      final ModelProvider modelProvider) {
    this.toolService = toolService;
    this.fileService = fileService;
    this.knowledgeService = knowledgeService;
    this.mediaServiceProvider = mediaServiceProvider;
    this.reminderSyncService = reminderSyncService;
    this.modelProvider = modelProvider;
  }

  public HumanInTheLoopTool getHITLTool() {
    return new HumanInTheLoopTool();
  }

  public BaseTool getSearchKnowledgeTool() {
    return new SearchKnowledgeTool(
        knowledgeService, fileService, mediaServiceProvider.get(), modelProvider);
  }

  public BaseTool getRefreshRemindersTool() {
    return new RefreshRemindersTool(reminderSyncService);
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
