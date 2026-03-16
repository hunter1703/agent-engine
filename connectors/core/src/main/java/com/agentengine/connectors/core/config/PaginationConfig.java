package com.agentengine.connectors.core.config;

public record PaginationConfig(String type, int maxPages, String pageParam, int pageStart, String pageSizeParam, int pageSize,
    String offsetParam, int offsetStart, String limitParam, int limit, String cursorParam, String nextCursor,
    String nextPageUrl) {

  public PaginationConfig {
    type = type == null || type.isBlank() ? PaginationType.UNKNOWN.name() : type;
    maxPages = maxPages <= 0 ? 100 : maxPages;
    pageStart = Math.max(1, pageStart);
    pageSize = Math.max(1, pageSize);
    offsetStart = Math.max(0, offsetStart);
    limit = Math.max(1, limit);
  }

  public PaginationConfig(final PaginationType type, final int maxPages, final String pageParam, final int pageStart,
      final String pageSizeParam, final int pageSize, final String offsetParam, final int offsetStart, final String limitParam,
      final int limit, final String cursorParam, final String nextCursor, final String nextPageUrl) {
    this(type == null ? null : type.name(), maxPages, pageParam, pageStart, pageSizeParam, pageSize, offsetParam, offsetStart, limitParam,
        limit, cursorParam, nextCursor, nextPageUrl);
  }

  public PaginationType typeEnum() {
    return PaginationType.valueOfOrDefault(type);
  }

  public static PaginationConfig none() {
    return new PaginationConfig(PaginationType.NONE, 1, null, 1, null, 100, null, 0, null, 100, null, null, null);
  }
}
