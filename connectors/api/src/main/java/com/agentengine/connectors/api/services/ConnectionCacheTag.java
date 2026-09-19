package com.agentengine.connectors.api.services;

import com.agentengine.util.distributed.CacheTag;
import java.util.Locale;

public enum ConnectionCacheTag implements CacheTag {
  CONNECTIONS,
  UNKNOWN;

  public static ConnectionCacheTag valueOfOrDefault(final String value) {
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
