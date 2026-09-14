package com.agentengine.util.distributed;

import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class DistributedCacheManager {
  private final ConcurrentMap<String, DistributedCache<?>> caches = new ConcurrentHashMap<>();
  private final JgroupsService jgroupsService;

  public DistributedCacheManager(JgroupsService jgroupsService) {
    jgroupsService.registerListener(
        EventCategory.CACHE_EVICTION,
        payload -> {
          final String[] split = payload.split(":", 1);
          final String cacheName = split[0];
          final String key = split[1];

          final DistributedCache<?> cache = caches.get(cacheName);

          if ("*".equals(key)) {
            cache.invalidateAll(true);
          } else {
            cache.invalidate(key, true);
          }
        });
    this.jgroupsService = jgroupsService;
  }

  public void register(final DistributedCache<?> distributedCache) {
    caches.put(distributedCache.getCacheName(), distributedCache);
  }

  public void broadcastInvalidation(final String cacheName, final String key) {
    jgroupsService.broadcast(EventCategory.CACHE_EVICTION, cacheName + ":" + key);
  }
}
