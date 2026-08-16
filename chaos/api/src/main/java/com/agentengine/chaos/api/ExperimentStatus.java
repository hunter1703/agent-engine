package com.agentengine.chaos.api;

import java.util.Locale;

public enum ExperimentStatus {
  UNKNOWN,
  SCHEDULED,
  RUNNING,
  PASSED,
  FAILED,
  ABORTED,
  DRY_RUN;

  public static ExperimentStatus valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return ExperimentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
