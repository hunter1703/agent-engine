package com.agentengine.connectors.core.template;

import java.util.Locale;

public enum ResolvedValueStatus {
  UNKNOWN, RESOLVED, NULL_VALUE, UNRESOLVED, OMITTED;

  public static ResolvedValueStatus valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return ResolvedValueStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
