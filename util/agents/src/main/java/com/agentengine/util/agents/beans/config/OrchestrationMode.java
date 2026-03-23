package com.agentengine.util.agents.beans.config;

import java.util.Locale;

/** Runtime orchestration strategy for orchestrator agents. */
public enum OrchestrationMode {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Handoff-capable manager pattern. */
  TRANSFER,
  /** Deterministic sequential execution over sub-agents. */
  SEQUENTIAL,
  /** Deterministic parallel execution over sub-agents. */
  PARALLEL;

  public static OrchestrationMode valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return OrchestrationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
