package com.agentengine.identity;

import com.agentengine.util.common.repository.DocumentStoreClientType;
import java.util.Locale;

public enum IdentityDocumentStoreClientType implements DocumentStoreClientType {
  IDENTITY,
  UNKNOWN;

  public static IdentityDocumentStoreClientType valueOfOrDefault(final String value) {
    if (value == null) {
      return UNKNOWN;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException exception) {
      return UNKNOWN;
    }
  }
}
