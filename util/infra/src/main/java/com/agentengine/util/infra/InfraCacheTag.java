package com.agentengine.util.infra;

import com.agentengine.util.distributed.CacheTag;
import java.util.Locale;

public enum InfraCacheTag implements CacheTag {
  INFRA_CONNECTION,
  UNKNOWN;

  public static InfraCacheTag valueOfOrDefault(final String value) {
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
