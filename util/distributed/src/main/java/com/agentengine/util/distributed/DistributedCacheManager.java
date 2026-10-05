package com.agentengine.util.distributed;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.util.common.beans.CacheTag;
import com.agentengine.util.common.utils.CollectionUtils;
import jakarta.annotation.PostConstruct;
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

  @PostConstruct
  public void init() {
    jgroupsService.start();
  }

  public DistributedCacheManager(JgroupsService jgroupsService) {
    jgroupsService.registerListener(
        EventCategory.CACHE_EVICTION,
        payload -> {
          final String[] split = payload.split(ID_SEPARATOR, 2);
          final String tag = split[0];
          final String key = split[1];

          for (final DistributedCache<?> cache :
              CollectionUtils.nullSafeList(tagVsCaches.get(tag))) {
            if ("*".equals(key)) {
              cache.invalidateAll(true);
            } else if (key.endsWith("*")) {
              cache.invalidateByPrefixLocally(key.substring(0, key.length() - 1));
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

  /**
   * Clears every cache registered under {@code name} — a cache's own name, or a tag several caches
   * share — on this node and on every other node.
   */
  public void invalidateAll(final String name) {
    for (final DistributedCache<?> cache : CollectionUtils.nullSafeList(tagVsCaches.get(name))) {
      cache.invalidateAll(true);
    }
    broadcastInvalidation(name, "*");
  }

  /**
   * Clears the current customer's entries of every customer-scoped cache registered under {@code
   * name}, on this node and on every other node.
   */
  public void invalidateInCustomerScope(final String name) {
    final String customerPrefix = CacheScope.CUSTOMER.namespace(name, "");
    for (final DistributedCache<?> cache : CollectionUtils.nullSafeList(tagVsCaches.get(name))) {
      cache.invalidateByPrefixLocally(customerPrefix);
    }
    broadcastInvalidation(name, customerPrefix + "*");
  }

  /**
   * Invalidates {@code key} in every cache registered under {@code name} on this node. If none are
   * registered here — the actual cache lives only in some other service's process, as {@link
   * com.agentengine.util.models.factories.ModelProvider}'s does relative to the catalog service —
   * there's nothing local to derive the right namespaced key from, so this broadcasts {@code key}
   * namespaced under every {@link CacheScope} a remote cache might use instead. A remote node whose
   * cache doesn't use that scope, or whose context can't resolve it, simply finds no matching entry
   * and does nothing — a redundant broadcast costs nothing more than rebuilding a cache entry
   * would.
   */
  public void invalidate(final String name, final String key) {
    final List<DistributedCache<?>> caches = tagVsCaches.get(name);
    if (CollectionUtils.isEmpty(caches)) {
      broadcastInvalidationForEveryScope(name, key, false);
      return;
    }
    for (final DistributedCache<?> cache : caches) {
      cache.invalidate(key);
    }
  }

  public void invalidateByPrefix(final String name, final String keyPrefix) {
    final List<DistributedCache<?>> caches = tagVsCaches.get(name);
    if (CollectionUtils.isEmpty(caches)) {
      broadcastInvalidationForEveryScope(name, keyPrefix, true);
      return;
    }
    for (final DistributedCache<?> cache : caches) {
      cache.invalidateByPrefix(keyPrefix);
    }
  }

  public void broadcastInvalidation(final String tag, final String key) {
    jgroupsService.broadcast(EventCategory.CACHE_EVICTION, tag + ID_SEPARATOR + key);
  }

  private void broadcastInvalidationForEveryScope(
      final String tag, final String key, final boolean asPrefix) {
    for (final CacheScope scope : CacheScope.values()) {
      if (scope == CacheScope.UNKNOWN) {
        continue;
      }
      try {
        final String namespacedKey = scope.namespace(tag, key);
        broadcastInvalidation(tag, asPrefix ? namespacedKey + "*" : namespacedKey);
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
