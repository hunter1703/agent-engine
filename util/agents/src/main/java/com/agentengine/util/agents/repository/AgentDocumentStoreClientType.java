package com.agentengine.util.agents.repository;

import com.agentengine.util.common.repository.DocumentStoreClientType;
import java.util.Locale;

public enum AgentDocumentStoreClientType implements DocumentStoreClientType {
  AGENT,
  UNKNOWN;

  public static AgentDocumentStoreClientType valueOfOrDefault(final String value) {
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
