package com.agentengine.connectors.core;

import com.agentengine.util.common.repository.DocumentStoreClientType;
import java.util.Locale;

public enum ConnectorsDocumentStoreClientType implements DocumentStoreClientType {
  CONNECTORS,
  UNKNOWN;

  public static ConnectorsDocumentStoreClientType valueOfOrDefault(final String value) {
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
