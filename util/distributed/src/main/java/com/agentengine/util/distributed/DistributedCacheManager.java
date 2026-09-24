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

  public void register(final DistributedCache<?> cache) {
    addToTag(cache, cache.getCacheName());
    for (final CacheTag tag : cache.getTags()) {
      addToTag(cache, tag.name());
    }
  }

  /** Clears every cache registered under {@code tag}, on this node and on every other node. */
  public void invalidateAll(final CacheTag tag) {
    for (final DistributedCache<?> cache :
        CollectionUtils.nullSafeList(tagVsCaches.get(tag.name()))) {
      cache.invalidateAll(true);
    }
    broadcastInvalidation(tag.name(), "*");
  }

  public void invalidate(final CacheTag tag, final String key) {
    for (final DistributedCache<?> cache :
        CollectionUtils.nullSafeList(tagVsCaches.get(tag.name()))) {
      cache.invalidate(key);
    }
  }

  public void broadcastInvalidation(final String tag, final String key) {
    jgroupsService.broadcast(EventCategory.CACHE_EVICTION, tag + ":" + key);
  }

  private void addToTag(final DistributedCache<?> cache, final String tag) {
    tagVsCaches.computeIfAbsent(tag, _ -> new CopyOnWriteArrayList<>()).add(cache);
  }
}
