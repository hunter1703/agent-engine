package com.agentengine.engine.tools.planning.beans;

import java.util.Locale;

public enum PlanStatus {
  UNKNOWN("unknown"),
  IN_PROGRESS("in_progress"),
  DONE("done"),
  ABANDONED("abandoned");

  private final String value;

  PlanStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static PlanStatus valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return PlanStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
