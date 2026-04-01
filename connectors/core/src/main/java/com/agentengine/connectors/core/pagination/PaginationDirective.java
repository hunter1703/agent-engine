package com.agentengine.connectors.core.pagination;

import java.util.Map;

public record PaginationDirective(Map<String, String> queryOverrides, String overrideUrl) {

    public PaginationDirective {
        queryOverrides = queryOverrides == null ? Map.of() : Map.copyOf(queryOverrides);
    }

    public static PaginationDirective empty() {
        return new PaginationDirective(Map.of(), null);
    }
}
