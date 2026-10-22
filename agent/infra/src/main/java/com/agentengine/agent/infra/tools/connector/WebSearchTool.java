package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.utils.CollectionUtils;
import com.google.genai.types.FunctionDeclaration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link ConnectorTool} hardwired to the brave_web_search connector. */
@DiscoverableTool
public final class WebSearchTool extends ConnectorTool {

  /**
   * Fixed and static so it's cheap to read at service startup - it must not require a call to the
   * connectors service, which may not be up yet or may not have this connector at all. Also keeps
   * the tool's identity as seen by the LLM decoupled from the specific connector backing it.
   */
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          "web_search",
          """
          Searches the web and returns excerpts of the most relevant results for one query. Use it \
          for external facts, documentation and current events. A query returns a limited set of \
          excerpts, so a broad question takes several searches: first to find what to look into, \
          then focused ones on each thing found.""",
          Map.of(),
          ToolRiskLevel.MEDIUM);

  private static final String APP_NAME = "brave";
  private static final String PREFERRED_CONNECTION_ID = "default_brave_connection";
  private static final String DEFAULT_COUNTRY = "US";
  private static final String DEFAULT_LANGUAGE = "en";
  private static final int DEFAULT_MAX_TOKENS = 8192;

  private final ConnectorCacheService connectorCacheService;

  public WebSearchTool(
      final ConnectionService connectionService,
      final ConnectorCacheService connectorCacheService,
      final ConnectorToolDeclarationCache connectorToolDeclarationCache) {
    super(
        connectorCacheService.getConnectorMetadata(APP_NAME, "brave_web_search"),
        connectionService,
        connectorToolDeclarationCache);
    this.connectorCacheService = connectorCacheService;
  }

  @Override
  public Optional<FunctionDeclaration> declaration() {
    return baseDeclaration();
  }

  @Override
  public ToolOutput<Map<String, Object>> execute(Map<String, Object> input) {
    input = new HashMap<>(CollectionUtils.nullSafeMap(input));
    input.putIfAbsent("country", DEFAULT_COUNTRY);
    input.putIfAbsent("search_lang", DEFAULT_LANGUAGE);
    input.putIfAbsent("maximum_number_of_tokens", DEFAULT_MAX_TOKENS);
    final List<String> connectionIds = connectorCacheService.getConnectionsForApp(APP_NAME);
    if (CollectionUtils.isEmpty(connectionIds)) {
      return ToolOutput.direct(Map.of("error", "No web search connection is available."));
    }
    final String connectionId =
        connectionIds.contains(PREFERRED_CONNECTION_ID)
            ? PREFERRED_CONNECTION_ID
            : connectionIds.getFirst();
    return super.execute(Map.of("input", input, "connectionId", connectionId));
  }
}
