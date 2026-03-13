package com.agentengine.util.common.update;

import java.util.Locale;

public enum OperationType {
  UNKNOWN, SET, UNSET;

  public static OperationType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return OperationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
