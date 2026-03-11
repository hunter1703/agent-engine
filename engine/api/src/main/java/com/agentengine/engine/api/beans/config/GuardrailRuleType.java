package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/** Guardrail rule type used to select the provider that builds/evaluates a rule. */
public enum GuardrailRuleType {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Text content checks (length/pattern policy) for input/output stages. */
  TEXT_CONTENT,
  /** Tool safety policy checks and escalation. */
  TOOL_SAFETY,
  /** Relevance/steering policy. */
  RELEVANCE;

  public static GuardrailRuleType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    final String normalized = value.trim().toUpperCase(Locale.ROOT);
    try {
      return GuardrailRuleType.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
