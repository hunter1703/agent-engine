package com.agentengine.connectors.core.pagination;

import com.agentengine.connectors.core.config.PaginationConfig;
import com.agentengine.connectors.core.config.PaginationType;
import java.util.Map;

public final class PagePaginationStrategy implements PaginationStrategy {

  @Override
  public PaginationType type() {
    return PaginationType.PAGE;
  }

  @Override
  public PaginationState initialState(final PaginationConfig config) {
    return new PaginationState(0, Math.max(1, config.pageStart()), 0, null, null, false);
  }

  @Override
  public PaginationDirective buildRequest(final PaginationConfig config, final PaginationState state) {
    return new PaginationDirective(
        Map.of(
            config.pageParam(), String.valueOf(state.pageNumber()),
            config.pageSizeParam(), String.valueOf(config.pageSize())),
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
        currentState.pageNumber() + 1,
        currentState.offset(),
        currentState.cursor(),
        currentState.nextPageUrl(),
        doneByData || doneByMaxPages);
  }
}
