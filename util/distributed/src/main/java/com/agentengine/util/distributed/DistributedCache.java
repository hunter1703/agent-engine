package com.agentengine.util.distributed;

import com.agentengine.util.common.Cache;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.context.Context;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A distributed cache decorator that uses a local Guava cache for fast reads, but broadcasts
 * evictions across the JGroups cluster when elements are removed or updated. This implementation
 * assumes Cache keys are of type String for cluster payload compatibility.
 *
 * <p>Every entry is implicitly namespaced by the ambient {@link Context}'s customer id - one shared
 * local cache holds every customer's entries under distinct physical keys, rather than one {@code
 * Cache} instance per customer, since these caches are unbounded (time-expiry only, no {@code
 * maximumSize}) and a per-customer instance would add a growing, never-cleaned-up map of Guava
 * caches for no eviction-isolation benefit today. {@link #getForUser}/{@link #putForUser}
 * additionally namespace by user id, for values that must stay private to one user of a customer
 * (e.g. once connection-level RBAC means not every user sees every connection).
 *
 * <p>{@link #invalidate} and {@link #put}'s broadcast only ever reach the customer-scoped entry for
 * a key, not every user-scoped variant of it under that customer - a user-scoped entry expires on
 * its own TTL instead. Reaching every user-scoped entry would need tracking which users have cached
 * a given key, which nothing here does yet; {@link #invalidateAll} remains the only way to
 * definitely clear user-scoped entries on demand.
 */
public class DistributedCache<V> {
  private static final String UNSCOPED = "_";

  private final String cacheName;
  private final Set<CacheTag> tags;
  private final Cache<String, V> localCache;
  private final DistributedCacheManager cacheManager;

  public DistributedCache(
      final String cacheName,
      final CacheBuilder<Object, Object> delegate,
      final Function<String, ? extends V> loader,
      final DistributedCacheManager cacheManager) {
    this(cacheName, Set.of(), delegate, loader, cacheManager);
  }

  public DistributedCache(
      final String cacheName,
      final Set<CacheTag> tags,
      final CacheBuilder<Object, Object> delegate,
      final Function<String, ? extends V> loader,
      final DistributedCacheManager cacheManager) {
    this.cacheName = cacheName;
    this.tags = CollectionUtils.nullSafeSet(tags);
    this.cacheManager = cacheManager;
    this.localCache =
        new Cache<>(delegate, namespacedKey -> loader.apply(unwrapKey(namespacedKey)));
  }

  @PostConstruct
  public void init() {
    cacheManager.register(this);
  }

  public String getCacheName() {
    return cacheName;
  }

  public Set<CacheTag> getTags() {
    return tags;
  }

  public V getIfPresent(String key) {
    return localCache.getIfPresent(namespacedKey(key));
  }

  public V get(String key) {
    return localCache.get(namespacedKey(key));
  }

  public V getForUser(String key) {
    return localCache.get(namespacedKeyForUser(key));
  }

  public Map<String, V> getAllPresent(Iterable<String> keys) {
    final Map<String, String> namespacedKeyVsKey = new LinkedHashMap<>();
    for (final String key : keys) {
      namespacedKeyVsKey.put(namespacedKey(key), key);
    }
    final Map<String, V> namespacedResults = localCache.getAllPresent(namespacedKeyVsKey.keySet());
    final Map<String, V> results = new LinkedHashMap<>();
    namespacedResults.forEach(
        (namespacedKey, value) -> results.put(namespacedKeyVsKey.get(namespacedKey), value));
    return results;
  }

  public void put(String key, V value) {
    final String namespacedKey = namespacedKey(key);
    localCache.put(namespacedKey, value);
    cacheManager.broadcastInvalidation(cacheName, namespacedKey);
  }

  public void putForUser(String key, V value) {
    localCache.put(namespacedKeyForUser(key), value);
  }

  public void invalidate(final String key) {
    final String namespacedKey = namespacedKey(key);
    localCache.invalidate(namespacedKey);
    cacheManager.broadcastInvalidation(cacheName, namespacedKey);
  }

  void invalidateNamespacedLocally(final String namespacedKey) {
    localCache.invalidate(namespacedKey);
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

  private static String namespacedKey(final String key) {
    return namespacedKey(customerId(), UNSCOPED, key);
  }

  private static String namespacedKeyForUser(final String key) {
    return namespacedKey(customerId(), userId(), key);
  }

  private static String namespacedKey(
      final String customerId, final String userId, final String key) {
    return customerId + ":" + userId + ":" + key;
  }

  private static String unwrapKey(final String namespacedKey) {
    return namespacedKey.split(":", 3)[2];
  }

  private static String customerId() {
    return Context.current()
        .map(Context::customerId)
        .filter(StringUtils::isNotBlank)
        .orElse(UNSCOPED);
  }

  private static String userId() {
    return Context.current().map(Context::userId).filter(StringUtils::isNotBlank).orElse(UNSCOPED);
  }
}
