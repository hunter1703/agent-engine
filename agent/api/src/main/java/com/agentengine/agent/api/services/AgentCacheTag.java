package com.agentengine.agent.api.services;

import com.agentengine.util.common.CacheTag;
import java.util.Locale;

public enum AgentCacheTag implements CacheTag {
  RUNNERS,
  UNKNOWN;

  public static AgentCacheTag valueOfOrDefault(final String value) {
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
