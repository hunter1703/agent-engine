package com.agentengine.engine.tools.lookup;

import com.agentengine.connectors.core.ConnectorService;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.AgentTool;
import com.agentengine.engine.api.tools.annotations.ToolConstructor;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import io.quarkus.arc.Arc;

import java.util.List;
import java.util.Map;

@AgentTool
public final class WebLookupTool extends Tool {
  private static final String TOOL_NAME = "web_lookup";
  private static final String CONNECTOR_ID = "duckduckgo_instant_search";
  private static final String DEFAULT_ERROR = "Unknown error";
  private static final String EMPTY_RESULT = "No abstract available for this query.";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Look up information on the web using DuckDuckGo.",
          List.of(ALL),
          Map.of());

  private final ConnectorService connectorService;

  public WebLookupTool() {
    this(resolveConnectorService());
  }

  @ToolConstructor
  public WebLookupTool(
      @ToolSchema(
              name = "connectorService",
              description = "Connector service for web lookup runtime",
              optional = true)
          final ConnectorService connectorService) {
    super(DESCRIPTOR);
    this.connectorService = connectorService == null ? resolveConnectorService() : connectorService;
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "query", description = "The search query to look up on the web.")
          final String query) {

    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("Query cannot be empty");
    }

    final ConnectorExecutionResult result =
        connectorService.execute(CONNECTOR_ID, Map.of("query", query));

    if (!result.success()) {
      return Map.of("error", result.errorMessage() == null ? DEFAULT_ERROR : result.errorMessage());
    }

    final Object mappedData = result.mappedData();
    if (mappedData == null || String.valueOf(mappedData).isBlank()) {
      return Map.of("result", EMPTY_RESULT);
    }

    return Map.of("result", mappedData);
  }

  private static ConnectorService resolveConnectorService() {
    final ConnectorService service = Arc.container().instance(ConnectorService.class).get();
    if (service == null) {
      throw new IllegalStateException("ConnectorService is not available");
    }
    return service;
  }
}
