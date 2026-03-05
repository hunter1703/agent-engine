package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/**
 * Guardrail execution style.
 */
public enum GuardrailExecutionMode {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Run guardrails before action execution; safest and deterministic. */
  SYNC,
  /** Run guardrails concurrently and interrupt execution on blocking decisions. */
  OPTIMISTIC;

  public static GuardrailExecutionMode valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return GuardrailExecutionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
