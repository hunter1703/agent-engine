package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.tools.ToolProvider;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.BaseTool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class WebSearchToolProvider implements ToolProvider {

  private final ConnectionService connectionService;
  private final ConnectorCacheService connectorCacheService;

  @Inject
  public WebSearchToolProvider(
      final ConnectionService connectionService,
      final ConnectorCacheService connectorCacheService) {
    this.connectionService = connectionService;
    this.connectorCacheService = connectorCacheService;
  }

  @Override
  public ToolDescriptor descriptor() {
    return WebSearchTool.DESCRIPTOR;
  }

  @Override
  public BaseTool create(final Map<String, Object> toolConfig) {
    return new WebSearchTool(connectionService, connectorCacheService);
  }
}
