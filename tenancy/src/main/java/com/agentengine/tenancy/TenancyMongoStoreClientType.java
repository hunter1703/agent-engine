package com.agentengine.tenancy;

import com.agentengine.util.mongodb.mongo.MongoStoreClientType;
import java.util.Locale;

public enum TenancyMongoStoreClientType implements MongoStoreClientType {
  TENANCY,
  UNKNOWN;

  public static TenancyMongoStoreClientType valueOfOrDefault(final String value) {
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
