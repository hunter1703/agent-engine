package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.tools.AbstractToolsetProvider;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.cloudstorage.FileService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

/** Toolset provider providing both web research search and webpage content fetching. */
@Singleton
public final class WebToolsetProvider extends AbstractToolsetProvider {

  private static final ToolDescriptor TOOLSET_DESCRIPTOR =
      new ToolDescriptor(
          Constants.Toolsets.WEB,
          "Web research tools for live search and webpage content retrieval.",
          Map.of());

  @Inject
  public WebToolsetProvider(
      final ConnectionService connectionService,
      final ConnectorCacheService connectorCacheService,
      final ConnectorToolDeclarationCache connectorToolDeclarationCache,
      final FileService fileService,
      final KnowledgeService knowledgeService,
      final CloudStorageService cloudStorageService) {
    super(
        TOOLSET_DESCRIPTOR,
        List.of(
            new ToolDefinition(
                WebSearchTool.DESCRIPTOR,
                () ->
                    new WebSearchTool(
                        connectionService, connectorCacheService, connectorToolDeclarationCache)),
            new ToolDefinition(
                GetWebpageTool.DESCRIPTOR,
                () -> new GetWebpageTool(fileService, knowledgeService, cloudStorageService))));
  }
}
