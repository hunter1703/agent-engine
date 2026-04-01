package com.agentengine.connectors.core.pagination;

import com.agentengine.connectors.core.config.PaginationConfig;
import com.agentengine.connectors.core.config.PaginationType;

public interface PaginationStrategy {

    PaginationType type();

    PaginationState initialState(PaginationConfig config);

    PaginationDirective buildRequest(PaginationConfig config, PaginationState state);

    PaginationState updateState(
            PaginationConfig config, PaginationState currentState, String responseBody, Object mappedData);
}
