package com.agentengine.connectors.core.validation;

import java.util.Locale;

public enum ValidationSeverity {
  UNKNOWN,
  INFO,
  WARNING,
  ERROR;

  public static ValidationSeverity valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return ValidationSeverity.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
