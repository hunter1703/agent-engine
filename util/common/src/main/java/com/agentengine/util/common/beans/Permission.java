package com.agentengine.util.common.beans;

import java.util.Locale;

public enum Permission {
  UNKNOWN,
  READ,
  WRITE,
  CREATE;

  public static Permission valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return Permission.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return UNKNOWN;
    }
  }
}
