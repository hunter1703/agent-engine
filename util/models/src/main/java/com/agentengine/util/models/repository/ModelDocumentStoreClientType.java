package com.agentengine.util.models.repository;

import com.agentengine.util.common.repository.DocumentStoreClientType;
import java.util.Locale;

public enum ModelDocumentStoreClientType implements DocumentStoreClientType {
  AGENT,
  MODEL,
  UNKNOWN;

  public static ModelDocumentStoreClientType valueOfOrDefault(final String value) {
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
