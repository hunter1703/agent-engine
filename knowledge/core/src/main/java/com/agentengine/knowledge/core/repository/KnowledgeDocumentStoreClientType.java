package com.agentengine.knowledge.core.repository;

import com.agentengine.util.common.repository.DocumentStoreClientType;
import java.util.Locale;

public enum KnowledgeDocumentStoreClientType implements DocumentStoreClientType {
  KNOWLEDGE,
  UNKNOWN;

  public static KnowledgeDocumentStoreClientType valueOfOrDefault(final String value) {
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
