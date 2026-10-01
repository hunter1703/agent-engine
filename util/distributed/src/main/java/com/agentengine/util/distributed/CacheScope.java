package com.agentengine.util.distributed;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.util.context.Context;
import java.util.Locale;

/** Whose entries a cache keeps apart: keys are namespaced by the scope. */
public enum CacheScope {
  /** One entry per key, shared by every customer and user. */
  GLOBAL(0),
  /** One entry per key for each customer. */
  CUSTOMER(1),
  UNKNOWN(0);

  private final int namespaceSegments;

  CacheScope(final int namespaceSegments) {
    this.namespaceSegments = namespaceSegments;
  }

  /** The key as this scope stores it, prefixed with whichever identity it isolates by. */
  public String namespace(final String cacheName, final String key) {
    return switch (this) {
      case GLOBAL -> key;
      case CUSTOMER -> customerId(cacheName) + ID_SEPARATOR + key;
      case UNKNOWN -> throw new IllegalStateException("Cache " + cacheName + " has no scope");
    };
  }

  /** The caller's own key back out of one this scope namespaced. */
  public String unwrap(final String namespacedKey) {
    return namespaceSegments == 0
        ? namespacedKey
        : namespacedKey.split(ID_SEPARATOR, namespaceSegments + 1)[namespaceSegments];
  }

  private String customerId(final String cacheName) {
    return Context.currentCustomerId().orElseThrow(() -> noIdentity(cacheName, "customer"));
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
