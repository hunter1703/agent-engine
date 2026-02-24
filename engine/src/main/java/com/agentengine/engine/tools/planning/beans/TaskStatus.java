package com.agentengine.engine.tools.planning.beans;

import java.util.Locale;

public enum TaskStatus {
  UNKNOWN("unknown"),
  TODO("todo"),
  IN_PROGRESS("in_progress"),
  DONE("done"),
  ABANDONED("abandoned");

  private final String value;

  TaskStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static TaskStatus valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return TaskStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
