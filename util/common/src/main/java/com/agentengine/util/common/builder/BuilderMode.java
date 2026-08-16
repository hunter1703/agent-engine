package com.agentengine.util.common.builder;

import java.util.Locale;

public enum BuilderMode {
  CREATE,
  EDIT,
  VIEW;

  public static BuilderMode fromString(final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return BuilderMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
