package com.agentengine.util.distributed;

import com.agentengine.util.common.Cache;
import com.agentengine.util.common.CollectionUtils;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class DistributedCache<V> {

  private final String cacheName;
  private final CacheScope scope;
  private final Set<CacheTag> tags;
  protected final Cache<String, V> localCache;
  private final DistributedCacheManager cacheManager;

  protected DistributedCache(final Builder<V> builder) {
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
    if (builder.reapWhen != null) {
      final ScheduledExecutorService reaper =
          Executors.newSingleThreadScheduledExecutor(
              Thread.ofVirtual().name(cacheName + "-reaper-", 0).factory());
      reaper.scheduleAtFixedRate(
          () -> localCache.invalidateIf(builder.reapWhen),
          builder.reapIntervalMillis,
          builder.reapIntervalMillis,
          TimeUnit.MILLISECONDS);
    }
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

  public void invalidateNamespacedLocally(final String namespacedKey) {
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

  protected String namespacedKey(final String key) {
    return scope.namespace(cacheName, key);
  }

  protected String unwrapKey(final String namespacedKey) {
    return scope.unwrap(namespacedKey);
  }

  public static class Builder<V> {
    private final String cacheName;
    private final DistributedCacheManager cacheManager;
    private CacheScope scope = CacheScope.CUSTOMER;
    private Set<CacheTag> tags = Set.of();
    private CacheBuilder<Object, Object> localCache = CacheBuilder.newBuilder();
    private Function<String, ? extends V> loader;
    private Consumer<? super V> removalListener = _ -> {};
    private Predicate<V> reapWhen;
    private long reapIntervalMillis;

    public Builder(final String cacheName, final DistributedCacheManager cacheManager) {
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

    public Builder<V> reapWhen(
        final Predicate<V> reapWhen, final long interval, final TimeUnit unit) {
      this.reapWhen = reapWhen;
      this.reapIntervalMillis = unit.toMillis(interval);
      return this;
    }

    public DistributedCache<V> build() {
      validate();
      return new DistributedCache<>(this);
    }

    protected void validate() {
      if (scope == CacheScope.UNKNOWN) {
        throw new IllegalArgumentException("Cache " + cacheName + " has no scope");
      }
      Objects.requireNonNull(localCache, "localCache");
    }
  }
}
