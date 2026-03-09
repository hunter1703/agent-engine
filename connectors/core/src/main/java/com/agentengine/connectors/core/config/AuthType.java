package com.agentengine.connectors.core.config;

import java.util.Locale;

public enum AuthType {
  UNKNOWN,
  NONE,
  API_KEY_HEADER,
  BEARER_TOKEN,
  BASIC,
  QUERY_PARAM,
  CUSTOM;

  public static AuthType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return AuthType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
