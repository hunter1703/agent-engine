package com.agentengine.util.distributed;

import java.util.Locale;

/** Whose entries a {@link DistributedCache} keeps apart: keys are namespaced by the scope. */
public enum CacheScope {
  /** One entry per key, shared by every customer and user. */
  GLOBAL(0),
  /** One entry per key for each customer. */
  CUSTOMER(1),
  /** One entry per key for each user of each customer. */
  USER(2),
  UNKNOWN(0);

  private final int namespaceSegments;

  CacheScope(final int namespaceSegments) {
    this.namespaceSegments = namespaceSegments;
  }

  int namespaceSegments() {
    return namespaceSegments;
  }

  public static CacheScope valueOfOrDefault(final String value) {
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
