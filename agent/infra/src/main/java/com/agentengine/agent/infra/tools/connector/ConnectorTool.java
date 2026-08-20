package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.google.genai.types.Schema;
import java.util.List;
import java.util.Map;

/**
 * Exposes a single configured connector as a tool - one instance per connector. Subclassable by
 * tools that want to hardwire a specific connector (e.g. {@link WebSearchTool}) while still
 * inheriting the connector-driven schema and call/error handling.
 */
public class ConnectorTool extends Tool {
  private static final String DEFAULT_ERROR = "Unknown error";

  private final ConnectorService connectorService;
  private final ConnectorMetadata connectorMetadata;

  public ConnectorTool(final ConnectorService connectorService, final ConnectorMetadata metadata) {
    this(connectorService, descriptorFor(metadata), metadata);
  }

  /**
   * For a subclass that wants a fixed, static tool identity (name/description known at compile
   * time, no remote call) decoupled from the connector backing it - e.g. {@link WebSearchTool},
   * whose descriptor must be cheap to read at service startup, before any connector is known to be
   * reachable. The args schema still comes from {@code metadata}.
   */
  protected ConnectorTool(
      final ConnectorService connectorService,
      final ToolDescriptor descriptor,
      final ConnectorMetadata metadata) {
    super(descriptor, argsSchemaFor(metadata));
    this.connectorService = connectorService;
    this.connectorMetadata = metadata;
  }

  public static ToolDescriptor descriptorFor(final ConnectorMetadata metadata) {
    return new ToolDescriptor(
        metadata.connectorName(), metadata.description(), Map.of(), ToolRiskLevel.MEDIUM);
  }

  public static Schema argsSchemaFor(final ConnectorMetadata metadata) {
    return Schema.fromJson(JsonUtils.toJson(metadata.inputSchema()));
  }

  public ToolOutput<Map<String, Object>> execute(final Map<String, Object> input) {
    try {
      final List<Map<String, Object>> results =
          connectorService
              .<Map<String, Object>>execute(
                  new ConnectorRequest(
                      connectorMetadata.appName(), connectorMetadata.connectorName(), input))
              .result();
      return ToolOutput.direct(
          Map.of(
              "result",
              CollectionUtils.nullSafeList(results).stream().findFirst().orElseGet(Map::of)));
    } catch (ConnectorException e) {
      return ToolOutput.direct(
          Map.of("error", StringUtils.isBlank(e.getMessage()) ? DEFAULT_ERROR : e.getMessage()));
    }
  }
}
