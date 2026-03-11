package com.agentengine.connectors.core.pagination;

import com.agentengine.connectors.core.config.PaginationConfig;
import com.agentengine.connectors.core.config.PaginationType;

final class NoPaginationStrategy implements PaginationStrategy {

  @Override
  public PaginationType type() {
    return PaginationType.NONE;
  }

  @Override
  public PaginationState initialState(final PaginationConfig config) {
    return new PaginationState(0, 1, 0, null, null, false);
  }

  @Override
  public PaginationDirective buildRequest(
      final PaginationConfig config, final PaginationState state) {
    return PaginationDirective.empty();
  }

  @Override
  public PaginationState updateState(
      final PaginationConfig config,
      final PaginationState currentState,
      final String responseBody,
      final Object mappedData) {
    return new PaginationState(
        currentState.iteration() + 1,
        currentState.pageNumber(),
        currentState.offset(),
        currentState.cursor(),
        currentState.nextPageUrl(),
        true);
  }
}
