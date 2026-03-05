package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/**
 * Guardrail rule type used to select the provider that builds/evaluates a rule.
 */
public enum GuardrailRuleType {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Input text rule checks (length/pattern policy). */
  INPUT_RULES,
  /** Output text rule checks (length/pattern policy). */
  OUTPUT_RULES,
  /** Tool risk policy checks and escalation. */
  TOOL_RISK,
  /** On-topic relevance/steering policy. */
  ON_TOPIC;

  public static GuardrailRuleType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return GuardrailRuleType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
