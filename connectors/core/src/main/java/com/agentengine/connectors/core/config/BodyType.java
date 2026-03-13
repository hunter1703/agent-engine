package com.agentengine.connectors.core.config;

import java.util.Locale;

public enum BodyType {
  UNKNOWN, JSON, FORM_URLENCODED, TEXT;

  public static BodyType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return BodyType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
