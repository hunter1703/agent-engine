package com.agentengine.connectors.core.pagination;

import com.agentengine.connectors.core.config.PaginationConfig;
import com.agentengine.connectors.core.config.PaginationType;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CursorPaginationStrategy implements PaginationStrategy {

  @Override
  public PaginationType type() {
    return PaginationType.CURSOR;
  }

  @Override
  public PaginationState initialState(final PaginationConfig config) {
    return PaginationState.initial();
  }

  @Override
  public PaginationDirective buildRequest(final PaginationConfig config, final PaginationState state) {
    final Map<String, String> queryParams = new LinkedHashMap<>();
    if (config.pageSizeParam() != null && !config.pageSizeParam().isBlank()) {
      queryParams.put(config.pageSizeParam(), String.valueOf(config.pageSize()));
    }
    if (state.cursor() != null && !state.cursor().isBlank()) {
      queryParams.put(config.cursorParam(), state.cursor());
    }
    return new PaginationDirective(queryParams, state.nextPageUrl());
  }

  @Override
  public PaginationState updateState(final PaginationConfig config, final PaginationState currentState, final String responseBody,
      final Object mappedData) {
    final int nextIteration = currentState.iteration() + 1;
    if (PaginationStrategySupport.reachedMaxPages(nextIteration, config.maxPages())) {
      return new PaginationState(nextIteration, currentState.pageNumber(), currentState.offset(), currentState.cursor(),
          currentState.nextPageUrl(), true);
    }

    final String nextCursor = PaginationStrategySupport.extractString(config.nextCursorJsonPath(), responseBody);
    final boolean done = nextCursor == null || nextCursor.isBlank();
    return new PaginationState(nextIteration, currentState.pageNumber(), currentState.offset(), nextCursor, currentState.nextPageUrl(),
        done);
  }
}
