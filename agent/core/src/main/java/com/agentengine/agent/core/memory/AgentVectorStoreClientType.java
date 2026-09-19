package com.agentengine.agent.core.memory;

import com.agentengine.util.vectordb.VectorStoreClientType;
import java.util.Locale;

public enum AgentVectorStoreClientType implements VectorStoreClientType {
  MEMORY,
  UNKNOWN;

  public static AgentVectorStoreClientType valueOfOrDefault(final String value) {
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
