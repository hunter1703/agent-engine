package com.agentengine.catalog.api.services;

import com.agentengine.util.distributed.CacheTag;
import java.util.Locale;

public enum RunnerCacheTag implements CacheTag {
  RUNNERS,
  UNKNOWN;

  public static RunnerCacheTag valueOfOrDefault(final String value) {
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
