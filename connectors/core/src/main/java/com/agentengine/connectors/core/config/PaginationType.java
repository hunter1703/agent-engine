package com.agentengine.connectors.core.config;

import java.util.Locale;

public enum PaginationType {
  UNKNOWN, NONE, PAGE, OFFSET, CURSOR, NEXT_URL;

  public static PaginationType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return PaginationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
