package com.agentengine.util.common;

import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public class Cache<K, V> {
  private final com.google.common.cache.Cache<K, Holder<? extends V>> delegate;
  private final Function<K, Holder<? extends V>> loader;

  public Cache(
      final com.google.common.cache.CacheBuilder<Object, Object> delegate,
      final Function<K, ? extends V> loader) {
    this.delegate = delegate.build();
    this.loader = input -> new Holder<>(loader.apply(input));
  }

  public @Nullable V getIfPresent(final K key) {
    final Holder<? extends V> holder = delegate.getIfPresent(key);
    return holder == null ? null : holder.value;
  }

  public V get(final K key) {
    try {
      final Holder<? extends V> holder = delegate.get(key, () -> loader.apply(key));
      return holder.value;
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  public Map<K, V> getAllPresent(final Iterable<K> keys) {
    final ImmutableMap<K, Holder<? extends V>> holders = delegate.getAllPresent(keys);
    final Map<K, V> result = new HashMap<>();
    for (Map.Entry<K, Holder<? extends V>> entry : holders.entrySet()) {
      if (entry.getValue() != null) {
        result.put(entry.getKey(), entry.getValue().value);
      }
    }
    return result;
  }

  public void put(final K key, final V value) {
    delegate.put(key, new Holder<>(value));
  }

  public void invalidate(final K key) {
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

  public void cleanUp() {
    delegate.cleanUp();
  }

  private record Holder<T>(T value) {}
}
