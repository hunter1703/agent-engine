package com.agentengine.agent.infra.tools.web;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.connectors.core.ConnectorService;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WebSearchTool extends Tool {
    private static final String TOOL_NAME = "web_research";
    private static final String BRAVE_CONNECTOR_ID = "brave_web_search";
    private static final String DUCKDUCKGO_CONNECTOR_ID = "duckduckgo_instant_search";
    private static final String DEFAULT_COUNTRY = "US";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final int DEFAULT_MAX_TOKENS = 8192;
    private static final String DEFAULT_ERROR = "Unknown error";

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Queries a live web search service and returns a synthesised summary of results for the given query. "
                    + "Use for retrieving current information, facts, documentation, news, research or any topic not available "
                    + "in your training data. Clear, specific queries produce better results than vague ones. "
                    + "Returns: { result } on success, where result is the search provider's synthesised response "
                    + "including titles, summaries, and source references; or { error } on failure.",
            Map.of(),
            ToolRiskLevel.LOW);

    private final ConnectorService connectorService;

    public WebSearchTool(final ConnectorService connectorService) {
        super(DESCRIPTOR);
        this.connectorService = connectorService;
    }

    public ToolOutput<Map<String, Object>> execute(
            @ToolSchema(
                            name = "query",
                            description =
                                    "The search query string. Phrase as a natural-language question or a set of keywords. "
                                            + "Specific queries produce better results. Must be non-empty.")
                    final String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
        return ToolOutput.direct(executeBraveSearch(query));
    }

    private Map<String, Object> executeBraveSearch(final String query) {
        final Map<String, Object> connectorInput = new LinkedHashMap<>();
        connectorInput.put("query", query.trim());
        connectorInput.put("country", DEFAULT_COUNTRY);
        connectorInput.put("search_lang", DEFAULT_LANGUAGE);
        connectorInput.put("maximum_number_of_tokens", DEFAULT_MAX_TOKENS);

        final ConnectorExecutionResult result =
                connectorService.execute(BRAVE_CONNECTOR_ID, Map.copyOf(connectorInput));
        if (!result.success()) {
            return Map.of("error", result.errorMessage() == null ? DEFAULT_ERROR : result.errorMessage());
        }
        return Map.of("result", result.mappedData());
    }

    private Map<String, Object> executeDuckDuckGoLookup(final String query) {
        final ConnectorExecutionResult result =
                connectorService.execute(DUCKDUCKGO_CONNECTOR_ID, Map.of("query", query.trim()));

        if (!result.success()) {
            return Map.of("error", result.errorMessage() == null ? DEFAULT_ERROR : result.errorMessage());
        }

        // noinspection unchecked
        final Map<String, Object> mappedData = CollectionUtils.nullSafeMap((Map<String, Object>) result.mappedData());
        if (StringUtils.isBlank(CollectionUtils.getStringValueFromMap(mappedData, "abstract"))) {
            // DuckDuckGo returned empty results, fallback to Brave
            return executeBraveSearch(query);
        }

        return Map.of("result", mappedData);
    }
}
