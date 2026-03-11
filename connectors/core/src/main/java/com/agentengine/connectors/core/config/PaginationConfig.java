package com.agentengine.connectors.core.config;

public record PaginationConfig(
    PaginationType type,
    int maxPages,
    String pageParam,
    int pageStart,
    String pageSizeParam,
    int pageSize,
    String offsetParam,
    int offsetStart,
    String limitParam,
    int limit,
    String cursorParam,
    String nextCursorJsonPath,
    String nextPageUrlJsonPath) {

  public PaginationConfig {
    type = type == null ? PaginationType.UNKNOWN : type;
    maxPages = maxPages <= 0 ? 100 : maxPages;
    pageStart = Math.max(1, pageStart);
    pageSize = Math.max(1, pageSize);
    offsetStart = Math.max(0, offsetStart);
    limit = Math.max(1, limit);
  }

  public static PaginationConfig none() {
    return new PaginationConfig(
        PaginationType.NONE, 1, null, 1, null, 100, null, 0, null, 100, null, null, null);
  }
}
