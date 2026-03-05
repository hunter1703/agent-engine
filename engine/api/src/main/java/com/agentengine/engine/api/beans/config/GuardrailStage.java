package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/**
 * Runtime stage at which a guardrail rule is evaluated.
 */
public enum GuardrailStage {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Evaluate before agent execution starts (user input validation). */
  INPUT,
  /** Evaluate before a tool call is executed. */
  TOOL,
  /** Evaluate before model output is emitted to the client. */
  OUTPUT;

  public static GuardrailStage valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return GuardrailStage.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
