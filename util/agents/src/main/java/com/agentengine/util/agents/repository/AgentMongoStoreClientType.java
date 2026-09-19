package com.agentengine.util.agents.repository;

import com.agentengine.util.mongodb.mongo.MongoStoreClientType;
import java.util.Locale;

public enum AgentMongoStoreClientType implements MongoStoreClientType {
  AGENT,
  UNKNOWN;

  public static AgentMongoStoreClientType valueOfOrDefault(final String value) {
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
