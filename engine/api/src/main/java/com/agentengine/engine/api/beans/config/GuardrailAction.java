package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/**
 * Action a guardrail rule can request when a policy condition is matched.
 */
public enum GuardrailAction {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Permit execution without emitting a guardrail signal. */
  ALLOW,
  /** Permit execution but emit a warning signal for observability/correction. */
  WARN,
  /** Stop execution at the current stage and return a block signal. */
  BLOCK,
  /** Escalate to human-in-the-loop style handling for manual intervention. */
  ESCALATE;

  public static GuardrailAction valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return GuardrailAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
