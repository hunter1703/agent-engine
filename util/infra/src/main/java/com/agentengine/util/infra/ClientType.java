package com.agentengine.util.infra;

import java.util.Locale;

public enum ClientType {
  MONGO_CLIENT,
  SQL_CLIENT,
  VECTOR_CLIENT,
  CLOUDSTORAGE_CLIENT,
  ENCRYPTION_CLIENT,
  MICROSERVICE_CLIENT,
  UNKNOWN;

  public static ClientType valueOfOrDefault(final String value) {
    if (value == null) {
      return UNKNOWN;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
