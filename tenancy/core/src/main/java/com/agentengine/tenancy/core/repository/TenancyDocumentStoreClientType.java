package com.agentengine.tenancy.core.repository;

import com.agentengine.util.common.repository.DocumentStoreClientType;
import java.util.Locale;

public enum TenancyDocumentStoreClientType implements DocumentStoreClientType {
  TENANCY,
  UNKNOWN;

  public static TenancyDocumentStoreClientType valueOfOrDefault(final String value) {
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
