package com.agentengine.util.distributed;

import com.agentengine.util.common.Cache;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.context.Context;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class DistributedCache<V> {
  private static final String SEPARATOR = ":";

  private final String cacheName;
  private final CacheScope scope;
  private final Set<CacheTag> tags;
  private final Cache<String, V> localCache;
  private final DistributedCacheManager cacheManager;

  private DistributedCache(final Builder<V> builder) {
    this.cacheName = builder.cacheName;
    this.scope = builder.scope;
    this.tags = CollectionUtils.nullSafeSet(builder.tags);
    this.cacheManager = builder.cacheManager;
    final Function<String, ? extends V> loader = builder.loader;
    this.localCache =
        new Cache<>(
            builder.localCache,
            namespacedKey -> loader.apply(unwrapKey(namespacedKey)),
            builder.removalListener);
    cacheManager.register(this);
  }

  public static <V> Builder<V> builder(
      final String cacheName, final DistributedCacheManager cacheManager) {
    return new Builder<>(cacheName, cacheManager);
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

  public V get(final String key, final Function<String, ? extends V> loader) {
    return localCache.get(
        namespacedKey(key), namespacedKey -> loader.apply(unwrapKey(namespacedKey)));
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

  private String namespacedKey(final String key) {
    return switch (scope) {
      case GLOBAL -> key;
      case CUSTOMER -> customerId() + SEPARATOR + key;
      case USER -> customerId() + SEPARATOR + userId() + SEPARATOR + key;
      case UNKNOWN -> throw new IllegalStateException("Cache " + cacheName + " has no scope");
    };
  }

  private String unwrapKey(final String namespacedKey) {
    final int segments = scope.namespaceSegments();
    return segments == 0 ? namespacedKey : namespacedKey.split(SEPARATOR, segments + 1)[segments];
  }

  private String customerId() {
    return Context.customerId().map(String::valueOf).orElseThrow(() -> noIdentity("customer"));
  }

  private String userId() {
    return Context.userId().map(String::valueOf).orElseThrow(() -> noIdentity("user"));
  }

  private IllegalStateException noIdentity(final String identity) {
    return new IllegalStateException(
        "Cache "
            + cacheName
            + " is "
            + scope
            + "-scoped but the current context has no "
            + identity);
  }

  public static final class Builder<V> {
    private final String cacheName;
    private final DistributedCacheManager cacheManager;
    private CacheScope scope = CacheScope.CUSTOMER;
    private Set<CacheTag> tags = Set.of();
    private CacheBuilder<Object, Object> localCache = CacheBuilder.newBuilder();
    private Function<String, ? extends V> loader;
    private Consumer<? super V> removalListener = _ -> {};

    private Builder(final String cacheName, final DistributedCacheManager cacheManager) {
      this.cacheName = cacheName;
      this.cacheManager = cacheManager;
    }

    public Builder<V> scope(final CacheScope scope) {
      this.scope = scope;
      return this;
    }

    public Builder<V> tags(final Set<CacheTag> tags) {
      this.tags = tags;
      return this;
    }

    public Builder<V> localCache(final CacheBuilder<Object, Object> localCache) {
      this.localCache = localCache;
      return this;
    }

    public Builder<V> loader(final Function<String, ? extends V> loader) {
      this.loader = loader;
      return this;
    }

    public Builder<V> removalListener(final Consumer<? super V> removalListener) {
      this.removalListener = removalListener;
      return this;
    }

    public DistributedCache<V> build() {
      if (scope == CacheScope.UNKNOWN) {
        throw new IllegalArgumentException("Cache " + cacheName + " has no scope");
      }
      Objects.requireNonNull(localCache, "localCache");
      return new DistributedCache<>(this);
    }
  }
}
