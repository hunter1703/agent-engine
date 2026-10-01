package com.agentengine.util.common.update;

import java.util.Locale;

public enum OperationType {
  UNKNOWN,
  SET,
  UNSET,
  INC,
  ADD_TO_SET,
  /** Removes every given value from a set. */
  REMOVE_FROM_SET,
  /** Sets a field only when the write creates the entity. */
  SET_ON_INSERT;

  public static OperationType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return OperationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
