package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.ExceptionUtils;
import com.agentengine.util.common.JsonUtils;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConnectorTool extends Tool {

  private final ConnectorMetadata connectorMetadata;
  private final ConnectorService connectorService;
  private final ConnectorCacheService connectorCacheService;

  public ConnectorTool(
      final String appName,
      final String connectorName,
      final ConnectorService connectorService,
      final ConnectorCacheService connectorCacheService) {
    this(
        connectorCacheService.getConnectorMetadata(appName, connectorName),
        connectorService,
        connectorCacheService);
  }

  public ConnectorTool(
      final ConnectorMetadata connectorMetadata,
      final ConnectorService connectorService,
      final ConnectorCacheService connectorCacheService) {
    this(
        descriptorFor(connectorMetadata),
        connectorMetadata,
        connectorService,
        connectorCacheService);
  }

  protected ConnectorTool(
      final ToolDescriptor toolDescriptor,
      final ConnectorMetadata connectorMetadata,
      final ConnectorService connectorService,
      final ConnectorCacheService connectorCacheService) {
    super(toolDescriptor, Schema.fromJson(JsonUtils.toJson(connectorMetadata.inputSchema())));
    this.connectorMetadata = connectorMetadata;
    this.connectorService = connectorService;
    this.connectorCacheService = connectorCacheService;
  }

  protected Optional<FunctionDeclaration> baseDeclaration() {
    return super.declaration();
  }

  @Override
  public Optional<FunctionDeclaration> declaration() {
    Optional<FunctionDeclaration> declaration = baseDeclaration();
    if (declaration.isEmpty()) {
      return declaration;
    }
    final FunctionDeclaration defaultDeclaration = declaration.get();

    final List<String> connectionIds =
        connectorCacheService.getConnectionsForApp(connectorMetadata.appName());

    final Map<String, Schema> properties = new HashMap<>();
    properties.put("input", defaultDeclaration.parameters().orElse(null));

    final List<String> requiredFields = new java.util.ArrayList<>();
    requiredFields.add("input");

    if (CollectionUtils.isNotEmpty(connectionIds)) {
      final Schema.Builder connectionIdSchemaBuilder =
          Schema.builder().type("STRING").description("The connection ID to use.");
      connectionIdSchemaBuilder.enum_(connectionIds);
      properties.put("connectionId", connectionIdSchemaBuilder.build());
    }

    final Schema schema =
        Schema.builder().type("OBJECT").properties(properties).required(requiredFields).build();

    FunctionDeclaration newDeclaration =
        FunctionDeclaration.builder()
            .name(defaultDeclaration.name().orElse(null))
            .description(defaultDeclaration.description().orElse(null))
            .parameters(schema)
            .build();

    return Optional.of(newDeclaration);
  }

  public ToolOutput<Map<String, Object>> execute(final Map<String, Object> args) {
    try {
      final Map<String, Object> input = CollectionUtils.getMapFromMap(args, "input");
      final String connectionId = CollectionUtils.getStringValueFromMap(args, "connectionId");

      final List<Map<String, Object>> results =
          connectorService
              .<Map<String, Object>>execute(
                  new ConnectorRequest(
                      connectorMetadata.appName(),
                      connectorMetadata.connectorName(),
                      connectionId,
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
