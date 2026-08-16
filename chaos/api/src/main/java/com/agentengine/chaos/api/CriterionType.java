package com.agentengine.chaos.api;

import java.util.Locale;

public enum CriterionType {
  UNKNOWN,
  MAX_ERROR_RATE,
  MAX_LATENCY_P99,
  MIN_SUCCESS_RATE,
  MAX_RECOVERY_TIME,
  ZERO_DATA_LOSS;

  public static CriterionType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return CriterionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
