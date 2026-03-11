package com.agentengine.engine.tools.lookup;

import com.agentengine.connectors.core.ConnectorService;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WebSearchTool extends Tool {
  private static final String TOOL_NAME = "web_search";
  private static final String CONNECTOR_ID = "brave_web_search";
  private static final String DEFAULT_COUNTRY = "US";
  private static final String DEFAULT_LANGUAGE = "en";
  private static final int DEFAULT_MAX_TOKENS = 8192;
  private static final String DEFAULT_ERROR = "Unknown error";

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Search the web using Brave Search API.",
          List.of(ALL),
          Map.of(),
          ToolRiskLevel.LOW);

  private final ConnectorService connectorService;

  public WebSearchTool(final ConnectorService connectorService) {
    super(DESCRIPTOR);
    this.connectorService = connectorService;
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "query", description = "The web search query.") final String query,
      @ToolSchema(
              name = "country",
              description = "Country code for search localization (e.g., US).",
              optional = true)
          final String country,
      @ToolSchema(
              name = "search_lang",
              description = "Language code for search localization (e.g., en).",
              optional = true)
          final String searchLang,
      @ToolSchema(
              name = "maximum_number_of_tokens",
              description = "Maximum number of tokens in Brave LLM context response.",
              optional = true)
          final Integer maximumNumberOfTokens) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("Query cannot be empty");
    }

    final Map<String, Object> connectorInput = new LinkedHashMap<>();
    connectorInput.put("query", query.trim());
    connectorInput.put(
        "country", country == null || country.isBlank() ? DEFAULT_COUNTRY : country.trim());
    connectorInput.put(
        "search_lang",
        searchLang == null || searchLang.isBlank() ? DEFAULT_LANGUAGE : searchLang.trim());
    connectorInput.put(
        "maximum_number_of_tokens",
        maximumNumberOfTokens == null || maximumNumberOfTokens <= 0
            ? DEFAULT_MAX_TOKENS
            : maximumNumberOfTokens);

    final ConnectorExecutionResult result =
        connectorService.execute(CONNECTOR_ID, Map.copyOf(connectorInput));
    if (!result.success()) {
      return Map.of("error", result.errorMessage() == null ? DEFAULT_ERROR : result.errorMessage());
    }
    return Map.of("result", result.mappedData());
  }
}
