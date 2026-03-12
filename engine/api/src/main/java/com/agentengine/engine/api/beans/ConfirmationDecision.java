package com.agentengine.engine.api.beans;

import java.util.Locale;

public enum ConfirmationDecision {
  UNKNOWN,
  ALLOW,
  DISALLOW;

  public static ConfirmationDecision valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return ConfirmationDecision.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return UNKNOWN;
    }
  }
}
