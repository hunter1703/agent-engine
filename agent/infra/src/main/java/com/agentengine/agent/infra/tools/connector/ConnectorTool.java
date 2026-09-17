package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.ExceptionUtils;
import com.agentengine.util.common.JsonUtils;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConnectorTool extends Tool {

  private final ConnectorMetadata connectorMetadata;
  private final ConnectionService connectionService;
  private final ConnectorToolDeclarationCache connectorToolDeclarationCache;

  public ConnectorTool(
      final String appName,
      final String connectorName,
      final ConnectionService connectionService,
      final ConnectorCacheService connectorCacheService,
      final ConnectorToolDeclarationCache connectorToolDeclarationCache) {
    this(
        connectorCacheService.getConnectorMetadata(appName, connectorName),
        connectionService,
        connectorToolDeclarationCache);
  }

  public ConnectorTool(
      final ConnectorMetadata connectorMetadata,
      final ConnectionService connectionService,
      final ConnectorToolDeclarationCache connectorToolDeclarationCache) {
    this(
        descriptorFor(connectorMetadata),
        connectorMetadata,
        connectionService,
        connectorToolDeclarationCache);
  }

  protected ConnectorTool(
      final ToolDescriptor toolDescriptor,
      final ConnectorMetadata connectorMetadata,
      final ConnectionService connectionService,
      final ConnectorToolDeclarationCache connectorToolDeclarationCache) {
    super(toolDescriptor, Schema.fromJson(JsonUtils.toJson(connectorMetadata.inputSchema())));
    this.connectorMetadata = connectorMetadata;
    this.connectionService = connectionService;
    this.connectorToolDeclarationCache = connectorToolDeclarationCache;
  }

  protected Optional<FunctionDeclaration> baseDeclaration() {
    return super.declaration();
  }

  @Override
  public Optional<FunctionDeclaration> declaration() {
    return connectorToolDeclarationCache.get(
        connectorMetadata.appName(), connectorMetadata.connectorName());
  }

  public ToolOutput<Map<String, Object>> execute(final Map<String, Object> args) {
    try {
      final Map<String, Object> input = CollectionUtils.getMapFromMap(args, "input");
      final String connectionId = CollectionUtils.getStringValueFromMap(args, "connectionId");

      final List<Map<String, Object>> results =
          connectionService
              .<Map<String, Object>>executeConnectorRequest(
                  new ConnectorRequest(
                      connectorMetadata.appName(),
                      connectorMetadata.connectorName(),
                      connectionId,
                      null,
                      input))
              .result();
      return ToolOutput.direct(
          Map.of(
              "result",
              CollectionUtils.nullSafeList(results).stream().findFirst().orElseGet(Map::of)));
    } catch (ConnectorException e) {
      return ToolOutput.direct(Map.of("error", ExceptionUtils.getErrorMessage(e)));
    }
  }

  private static ToolDescriptor descriptorFor(final ConnectorMetadata connectorMetadata) {
    return new ToolDescriptor(
        connectorMetadata.connectorName(),
        connectorMetadata.description(),
        Map.of(),
        ToolRiskLevel.MEDIUM);
  }
}
