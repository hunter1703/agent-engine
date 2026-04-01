package com.agentengine.runtime.tools.lookup;

import com.agentengine.connectors.core.ConnectorService;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
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
            TOOL_NAME, "Search the web for information on a given topic.", Map.of(), ToolRiskLevel.LOW);

    private final ConnectorService connectorService;

    public WebSearchTool(final ConnectorService connectorService) {
        super(DESCRIPTOR);
        this.connectorService = connectorService;
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "query", description = "The search query.") final String query,
            @ToolSchema(name = "country", description = "Country code for search localization.", optional = true)
                    final String country,
            @ToolSchema(name = "search_lang", description = "Language code for search localization.", optional = true)
                    final String searchLang,
            @ToolSchema(
                            name = "maximum_number_of_tokens",
                            description = "Maximum number of tokens in the response.",
                            optional = true)
                    final Integer maximumNumberOfTokens,
            @ToolSchema(
                            name = "detailed",
                            description =
                                    "If true, returns comprehensive search results (uses more resources). If false, returns a quick summary. Defaults to false.",
                            optional = true)
                    final Boolean detailed) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }

        final boolean useDetailed = detailed != null && detailed;

        if (useDetailed) {
            return executeBraveSearch(query, country, searchLang, maximumNumberOfTokens);
        } else {
            return executeDuckDuckGoLookup(query);
        }
    }

    private Map<String, Object> executeBraveSearch(
            final String query, final String country, final String searchLang, final Integer maximumNumberOfTokens) {
        final Map<String, Object> connectorInput = new LinkedHashMap<>();
        connectorInput.put("query", query.trim());
        connectorInput.put("country", country == null || country.isBlank() ? DEFAULT_COUNTRY : country.trim());
        connectorInput.put(
                "search_lang", searchLang == null || searchLang.isBlank() ? DEFAULT_LANGUAGE : searchLang.trim());
        connectorInput.put(
                "maximum_number_of_tokens",
                maximumNumberOfTokens == null || maximumNumberOfTokens <= 0
                        ? DEFAULT_MAX_TOKENS
                        : maximumNumberOfTokens);

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
            return executeBraveSearch(query, null, null, null);
        }

        return Map.of("result", mappedData);
    }
}
