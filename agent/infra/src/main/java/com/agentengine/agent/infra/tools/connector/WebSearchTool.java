package com.agentengine.agent.infra.tools.connector;

import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@link ConnectorTool} hardwired to the brave_web_search connector. */
public final class WebSearchTool extends ConnectorTool {

  /**
   * Fixed and static so it's cheap to read at service startup (see {@link
   * WebSearchToolProvider#descriptor()}) - it must not require a call to the connectors service,
   * which may not be up yet or may not have this connector at all. Also keeps the tool's identity
   * as seen by the LLM ("web_research") decoupled from the specific connector backing it.
   */
  static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          "web_research",
          "Query a live web search service and return a synthesised summary of results for a"
              + " given query.",
          Map.of(),
          ToolRiskLevel.MEDIUM);

  private static final String DEFAULT_COUNTRY = "US";
  private static final String DEFAULT_LANGUAGE = "en";
  private static final int DEFAULT_MAX_TOKENS = 8192;

  public WebSearchTool(final ConnectorService connectorService) {
    super(connectorService, DESCRIPTOR, connectorService.describe("brave", "brave_web_search"));
  }

  @Override
  public ToolOutput<Map<String, Object>> execute(final Map<String, Object> input) {
    final Map<String, Object> withDefaults =
        new LinkedHashMap<>(CollectionUtils.nullSafeMap(input));
    withDefaults.putIfAbsent("country", DEFAULT_COUNTRY);
    withDefaults.putIfAbsent("search_lang", DEFAULT_LANGUAGE);
    withDefaults.putIfAbsent("maximum_number_of_tokens", DEFAULT_MAX_TOKENS);
    return super.execute(withDefaults);
  }
}
