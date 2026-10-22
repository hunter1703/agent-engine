package com.agentengine.agent.core.memory;

import java.util.Locale;

/** What the memory agent decides to do with one memory. */
public enum MemoryOperation {
  UNKNOWN,
  ADD,
  UPDATE,
  DELETE,
  NOOP;

  public static MemoryOperation valueOfOrDefault(final String value) {
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
