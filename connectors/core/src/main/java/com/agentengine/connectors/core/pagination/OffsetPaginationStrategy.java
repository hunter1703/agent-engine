package com.agentengine.connectors.core.pagination;

import com.agentengine.connectors.core.config.PaginationConfig;
import com.agentengine.connectors.core.config.PaginationType;
import java.util.Map;

public final class OffsetPaginationStrategy implements PaginationStrategy {

  @Override
  public PaginationType type() {
    return PaginationType.OFFSET;
  }

  @Override
  public PaginationState initialState(final PaginationConfig config) {
    return new PaginationState(0, 1, Math.max(0, config.offsetStart()), null, null, false);
  }

  @Override
  public PaginationDirective buildRequest(
      final PaginationConfig config, final PaginationState state) {
    return new PaginationDirective(
        Map.of(
            config.offsetParam(), String.valueOf(state.offset()),
            config.limitParam(), String.valueOf(config.limit())),
        null);
  }

  @Override
  public PaginationState updateState(
      final PaginationConfig config,
      final PaginationState currentState,
      final String responseBody,
      final Object mappedData) {
    final int nextIteration = currentState.iteration() + 1;
    final boolean doneByData = PaginationStrategySupport.isTerminalData(mappedData);
    final boolean doneByMaxPages =
        PaginationStrategySupport.reachedMaxPages(nextIteration, config.maxPages());
    return new PaginationState(
        nextIteration,
        currentState.pageNumber(),
        currentState.offset() + config.limit(),
        currentState.cursor(),
        currentState.nextPageUrl(),
        doneByData || doneByMaxPages);
  }
}
