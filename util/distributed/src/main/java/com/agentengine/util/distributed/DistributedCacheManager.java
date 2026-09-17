package com.agentengine.util.distributed;

import com.agentengine.util.common.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class DistributedCacheManager {
  private final ConcurrentMap<String, List<DistributedCache<?>>> tagVsCaches =
      new ConcurrentHashMap<>();
  private final JgroupsService jgroupsService;

  public DistributedCacheManager(JgroupsService jgroupsService) {
    jgroupsService.registerListener(
        EventCategory.CACHE_EVICTION,
        payload -> {
          final String[] split = payload.split(":", 2);
          final String tag = split[0];
          final String key = split[1];

          for (final DistributedCache<?> cache :
              CollectionUtils.nullSafeList(tagVsCaches.get(tag))) {
            if ("*".equals(key)) {
              cache.invalidateAll(true);
            } else {
              cache.invalidateNamespacedLocally(key);
            }
          }
        });
    this.jgroupsService = jgroupsService;
  }

  public void register(final DistributedCache<?> distributedCache) {
    addToTag(distributedCache, distributedCache.getCacheName());
    for (final CacheTag tag : distributedCache.getTags()) {
      addToTag(distributedCache, tag.name());
    }
  }

  public void broadcastInvalidation(final String tag, final String key) {
    jgroupsService.broadcast(EventCategory.CACHE_EVICTION, tag + ":" + key);
  }

  private void addToTag(final DistributedCache<?> distributedCache, final String tag) {
    tagVsCaches.computeIfAbsent(tag, _ -> new CopyOnWriteArrayList<>()).add(distributedCache);
  }
}
