package com.agentengine.engine.utils;

import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public class Cache<K, V> {
  private final com.google.common.cache.Cache<K, V> delegate;
  private final Function<K, ? extends V> loader;

  public Cache(com.google.common.cache.Cache<K, V> delegate, Function<K, ? extends V> loader) {
    this.delegate = delegate;
    this.loader = loader;
  }

  public @Nullable V getIfPresent(final Object key) {
    return delegate.getIfPresent(key);
  }

  public V get(final K key) {
    try {
      return delegate.get(key, () -> loader.apply(key));
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public ImmutableMap<K, V> getAllPresent(final Iterable<?> keys) {
    return delegate.getAllPresent(keys);
  }

  public void put(final K key, final V value) {
    delegate.put(key, value);
  }

  public void putAll(final Map<? extends K, ? extends V> m) {
    delegate.putAll(m);
  }

  public void invalidate(final String key) {
    delegate.invalidate(key);
  }

  public void invalidateAll(final Iterable<?> keys) {
    delegate.invalidateAll(keys);
  }

  public void invalidateAll() {
    delegate.invalidateAll();
  }

  public long size() {
    return delegate.size();
  }

  public CacheStats stats() {
    return delegate.stats();
  }

  public ConcurrentMap<K, V> asMap() {
    return delegate.asMap();
  }

  public void cleanUp() {
    delegate.cleanUp();
  }
}
