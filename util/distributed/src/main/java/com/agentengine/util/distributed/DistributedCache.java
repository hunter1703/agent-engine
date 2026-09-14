package com.agentengine.util.distributed;

import com.agentengine.util.common.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.function.Function;

/**
 * A distributed cache decorator that uses a local Guava cache for fast reads, but broadcasts
 * evictions across the JGroups cluster when elements are removed or updated. This implementation
 * assumes Cache keys are of type String for cluster payload compatibility.
 */
public class DistributedCache<V> {
  private final String cacheName;
  private final Cache<String, V> localCache;
  private final DistributedCacheManager cacheManager;

  public DistributedCache(
      final String cacheName,
      final CacheBuilder<Object, Object> delegate,
      final Function<String, ? extends V> loader,
      final DistributedCacheManager cacheManager) {
    this.cacheName = cacheName;
    this.cacheManager = cacheManager;
    this.localCache = new Cache<>(delegate, loader);
  }

  @PostConstruct
  public void init() {
    cacheManager.register(this);
  }

  public String getCacheName() {
    return cacheName;
  }

  public V getIfPresent(String key) {
    return localCache.getIfPresent(key);
  }

  public V get(String key) {
    return localCache.get(key);
  }

  public Map<String, V> getAllPresent(Iterable<String> keys) {
    return localCache.getAllPresent(keys);
  }

  public void put(String key, V value) {
    localCache.put(key, value);
    cacheManager.broadcastInvalidation(cacheName, key);
  }

  public void invalidate(final String key, final boolean localOnly) {
    localCache.invalidate(key);
    if (!localOnly) {
      cacheManager.broadcastInvalidation(cacheName, key);
    }
  }

  public void invalidateAll(final boolean localOnly) {
    localCache.invalidateAll();
    if (!localOnly) {
      cacheManager.broadcastInvalidation(cacheName, "*");
    }
  }

  public long size() {
    return localCache.size();
  }

  public CacheStats stats() {
    return localCache.stats();
  }

  public void cleanUp() {
    localCache.cleanUp();
  }
}
