package com.agentengine.connectors.core.pagination;

public record PaginationState(int iteration, int pageNumber, int offset, String cursor, String nextPageUrl, boolean done) {

  public PaginationState {
    iteration = Math.max(0, iteration);
    pageNumber = Math.max(1, pageNumber);
    offset = Math.max(0, offset);
  }

  public static PaginationState initial() {
    return new PaginationState(0, 1, 0, null, null, false);
  }
}
