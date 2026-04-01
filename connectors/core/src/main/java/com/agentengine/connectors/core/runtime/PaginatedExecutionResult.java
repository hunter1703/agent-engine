package com.agentengine.connectors.core.runtime;

import java.util.List;

public record PaginatedExecutionResult(
        List<ConnectorExecutionResult> pageResults, List<Object> aggregatedItems, boolean truncated, int pagesFetched) {

    public PaginatedExecutionResult {
        pageResults = pageResults == null ? List.of() : List.copyOf(pageResults);
        aggregatedItems = aggregatedItems == null ? List.of() : List.copyOf(aggregatedItems);
    }
}
