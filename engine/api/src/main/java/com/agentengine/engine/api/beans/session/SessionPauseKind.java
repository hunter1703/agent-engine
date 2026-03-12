package com.agentengine.engine.api.beans.session;

import java.util.Locale;

public enum SessionPauseKind {
  UNKNOWN,
  DECISION,
  TEXT;

  public static SessionPauseKind valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return SessionPauseKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return UNKNOWN;
    }
  }
}
