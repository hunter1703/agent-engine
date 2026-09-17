package com.agentengine.util.distributed;

/**
 * A tag a {@link DistributedCache} can be registered under, in addition to its own name, so one
 * invalidation broadcast can evict every cache sharing that tag rather than one broadcast per
 * cache.
 */
public enum CacheTag {
  CONNECTIONS,
  UNKNOWN;

  public static CacheTag valueOfOrDefault(final String name) {
    try {
      return CacheTag.valueOf(name);
    } catch (IllegalArgumentException | NullPointerException e) {
      return UNKNOWN;
    }
  }
}
