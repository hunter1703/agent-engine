package com.agentengine.connectors.core;

import com.agentengine.util.mongodb.mongo.MongoStoreClientType;
import java.util.Locale;

public enum ConnectorsMongoStoreClientType implements MongoStoreClientType {
  CONNECTORS,
  UNKNOWN;

  public static ConnectorsMongoStoreClientType valueOfOrDefault(final String value) {
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
