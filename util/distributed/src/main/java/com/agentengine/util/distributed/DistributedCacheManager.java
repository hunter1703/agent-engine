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

  /**
   * Invalidates {@code key} in every cache registered under {@code tag} on this node. If none are
   * registered here — the tag's actual cache lives only in some other service's process, as {@link
   * com.agentengine.util.models.factories.ModelProvider}'s does relative to the catalog service —
   * there's nothing local to derive the right namespaced key from, so this broadcasts {@code key}
   * namespaced under every {@link CacheScope} a remote cache might use instead. A remote node whose
   * cache doesn't use that scope, or whose context can't resolve it, simply finds no matching entry
   * and does nothing — a redundant broadcast costs nothing more than rebuilding a cache entry would.
   */
  public void invalidate(final CacheTag tag, final String key) {
    final List<DistributedCache<?>> caches = tagVsCaches.get(tag.name());
    if (CollectionUtils.isEmpty(caches)) {
      broadcastInvalidationForEveryScope(tag.name(), key);
      return;
    }
    for (final DistributedCache<?> cache : caches) {
      cache.invalidate(key);
    }
  }

  public void broadcastInvalidation(final String tag, final String key) {
    jgroupsService.broadcast(EventCategory.CACHE_EVICTION, tag + ":" + key);
  }

  private void broadcastInvalidationForEveryScope(final String tag, final String key) {
    for (final CacheScope scope : CacheScope.values()) {
      if (scope == CacheScope.UNKNOWN) {
        continue;
      }
      try {
        broadcastInvalidation(tag, scope.namespace(tag, key));
      } catch (final IllegalStateException ex) {
        // The current context can't resolve this scope's customer/user id, so no cache using it
        // could have namespaced this key that way either — nothing to invalidate for it here.
      }
    }
  }

  private void addToTag(final DistributedCache<?> cache, final String tag) {
    tagVsCaches.computeIfAbsent(tag, _ -> new CopyOnWriteArrayList<>()).add(cache);
  }
}
