package com.agentengine.catalog.core.repository;

import com.agentengine.util.mongodb.mongo.MongoStoreClientType;
import java.util.Locale;

public enum CatalogMongoStoreClientType implements MongoStoreClientType {
  CATALOG,
  UNKNOWN;

  public static CatalogMongoStoreClientType valueOfOrDefault(final String value) {
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
