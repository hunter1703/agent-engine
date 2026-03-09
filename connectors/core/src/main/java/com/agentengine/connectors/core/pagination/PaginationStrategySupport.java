package com.agentengine.connectors.core.pagination;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.util.Collection;

final class PaginationStrategySupport {

  private PaginationStrategySupport() {}

  static boolean isTerminalData(final Object mappedData) {
    if (mappedData == null) {
      return true;
    }
    if (mappedData instanceof Collection<?> collection) {
      return collection.isEmpty();
    }
    return false;
  }

  static String extractString(final String jsonPath, final String responseBody) {
    if (jsonPath == null || jsonPath.isBlank() || responseBody == null || responseBody.isBlank()) {
      return null;
    }
    try {
      final Object value = JsonPath.read(responseBody, jsonPath);
      return value == null ? null : String.valueOf(value);
    } catch (PathNotFoundException ex) {
      return null;
    } catch (RuntimeException ex) {
      throw new PaginationStrategyException("Failed to extract pagination token with JsonPath", ex);
    }
  }

  static boolean reachedMaxPages(final int nextIteration, final int maxPages) {
    return nextIteration >= Math.max(1, maxPages);
  }
}
