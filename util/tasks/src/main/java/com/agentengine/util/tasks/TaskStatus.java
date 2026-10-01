package com.agentengine.util.tasks;

import java.util.Locale;

/** Where a {@link Task} stands: waiting to be processed, or processed. */
public enum TaskStatus {
  UNKNOWN,
  PENDING,
  DONE;

  public static TaskStatus valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException exception) {
      return UNKNOWN;
    }
  }
}
