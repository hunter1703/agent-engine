package com.agentengine.scheduler.core.store;

import com.agentengine.util.mongodb.mongo.MongoStoreClientType;
import java.util.Locale;

public enum SchedulerMongoStoreClientType implements MongoStoreClientType {
  SCHEDULER,
  UNKNOWN;

  public static SchedulerMongoStoreClientType valueOfOrDefault(final String value) {
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
