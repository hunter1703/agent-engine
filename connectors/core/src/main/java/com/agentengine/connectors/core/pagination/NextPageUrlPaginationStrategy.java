package com.agentengine.connectors.core.pagination;

import com.agentengine.connectors.core.config.PaginationConfig;
import com.agentengine.connectors.core.config.PaginationType;

public final class NextPageUrlPaginationStrategy implements PaginationStrategy {

  @Override
  public PaginationType type() {
    return PaginationType.NEXT_URL;
  }

  @Override
  public PaginationState initialState(final PaginationConfig config) {
    return PaginationState.initial();
  }

  @Override
  public PaginationDirective buildRequest(final PaginationConfig config, final PaginationState state) {
    return new PaginationDirective(java.util.Map.of(), state.nextPageUrl());
  }

  @Override
  public PaginationState updateState(final PaginationConfig config, final PaginationState currentState, final String responseBody,
      final Object mappedData) {
    final int nextIteration = currentState.iteration() + 1;
    if (PaginationStrategySupport.reachedMaxPages(nextIteration, config.maxPages())) {
      return new PaginationState(nextIteration, currentState.pageNumber(), currentState.offset(), currentState.cursor(),
          currentState.nextPageUrl(), true);
    }

    final String nextPageUrl = PaginationStrategySupport.extractString(config.nextPageUrl(), responseBody);
    final boolean done = nextPageUrl == null || nextPageUrl.isBlank();
    return new PaginationState(nextIteration, currentState.pageNumber(), currentState.offset(), currentState.cursor(), nextPageUrl, done);
  }
}
