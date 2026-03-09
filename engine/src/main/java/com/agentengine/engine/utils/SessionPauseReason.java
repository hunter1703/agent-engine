package com.agentengine.engine.utils;

import java.util.Locale;

public enum SessionPauseReason {
  UNKNOWN("unknown"),
  USER_CLARIFICATION("user_clarification"),
  TOOL_CONFIRMATION("tool_confirmation"),
  GUARDRAIL_ESCALATION("guardrail_escalation");

  private final String code;

  SessionPauseReason(final String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static SessionPauseReason valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    final String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    for (final SessionPauseReason reason : values()) {
      if (reason.name().equals(normalized)) {
        return reason;
      }
      if (reason.code.equalsIgnoreCase(value.trim())) {
        return reason;
      }
    }
    return UNKNOWN;
  }
}
