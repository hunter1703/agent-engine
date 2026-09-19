package com.agentengine.knowledge.core.repository;

import com.agentengine.util.mongodb.mongo.MongoStoreClientType;
import java.util.Locale;

public enum KnowledgeMongoStoreClientType implements MongoStoreClientType {
  KNOWLEDGE,
  UNKNOWN;

  public static KnowledgeMongoStoreClientType valueOfOrDefault(final String value) {
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
