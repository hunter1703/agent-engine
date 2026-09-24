package com.agentengine.util.distributed;

import com.agentengine.util.context.Context;
import java.util.Locale;

/** Whose entries a cache keeps apart: keys are namespaced by the scope. */
public enum CacheScope {
  /** One entry per key, shared by every customer and user. */
  GLOBAL(0),
  /** One entry per key for each customer. */
  CUSTOMER(1),
  /** One entry per key for each user of each customer. */
  USER(2),
  UNKNOWN(0);

  private static final String SEPARATOR = ":";

  private final int namespaceSegments;

  CacheScope(final int namespaceSegments) {
    this.namespaceSegments = namespaceSegments;
  }

  /** The key as this scope stores it, prefixed with whichever of customer/user it isolates by. */
  public String namespace(final String cacheName, final String key) {
    return switch (this) {
      case GLOBAL -> key;
      case CUSTOMER -> customerId(cacheName) + SEPARATOR + key;
      case USER -> customerId(cacheName) + SEPARATOR + userId(cacheName) + SEPARATOR + key;
      case UNKNOWN -> throw new IllegalStateException("Cache " + cacheName + " has no scope");
    };
  }

  /** The caller's own key back out of one this scope namespaced. */
  public String unwrap(final String namespacedKey) {
    return namespaceSegments == 0
        ? namespacedKey
        : namespacedKey.split(SEPARATOR, namespaceSegments + 1)[namespaceSegments];
  }

  private String customerId(final String cacheName) {
    return Context.customerId()
        .map(String::valueOf)
        .orElseThrow(() -> noIdentity(cacheName, "customer"));
  }

  private String userId(final String cacheName) {
    return Context.userId().map(String::valueOf).orElseThrow(() -> noIdentity(cacheName, "user"));
  }

  private IllegalStateException noIdentity(final String cacheName, final String identity) {
    return new IllegalStateException(
        "Cache "
            + cacheName
            + " is "
            + this
            + "-scoped but the current context has no "
            + identity);
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
