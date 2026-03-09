package com.agentengine.connectors.core.config;

import java.util.Locale;

public enum RetryBackoffType {
  UNKNOWN,
  FIXED,
  EXPONENTIAL;

  public static RetryBackoffType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return RetryBackoffType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
