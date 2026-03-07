package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.StringUtils;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic ref-counted cache with idle eviction.
 *
 * <p>getAndAcquire increments a lease counter for the key. Release decrements it (floored at zero).
 * Entries are evicted only when refCount reaches zero and idle timeout is exceeded.
 */
public final class RefCountedCache<K, V> implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(RefCountedCache.class);
  private static final long DEFAULT_IDLE_TIMEOUT = 10L;
  private static final TimeUnit DEFAULT_IDLE_TIMEOUT_UNIT = TimeUnit.MINUTES;
  private static final long DEFAULT_CLEANUP_INTERVAL = 30L;
  private static final TimeUnit DEFAULT_CLEANUP_INTERVAL_UNIT = TimeUnit.SECONDS;

  private final String name;
  private final long idleTimeoutMillis;
  private final Function<K, V> creator;
  private final BiConsumer<K, V> evictHandler;
  private final ConcurrentMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupExecutor;

  private RefCountedCache(
      final String name,
      final long idleTimeout,
      final TimeUnit idleTimeoutUnit,
      final long cleanupInterval,
      final TimeUnit cleanupIntervalUnit,
      final Function<K, V> creator,
      final BiConsumer<K, V> evictHandler) {
    this.name = StringUtils.isBlank(name) ? "ref-counted-cache" : name;
    this.idleTimeoutMillis = Math.max(1L, idleTimeoutUnit.toMillis(Math.max(1L, idleTimeout)));
    final long cleanupIntervalMillis =
        Math.max(1L, cleanupIntervalUnit.toMillis(Math.max(1L, cleanupInterval)));
    this.creator = Objects.requireNonNull(creator, "creator cannot be null");
    this.evictHandler = evictHandler == null ? (_key, _value) -> {} : evictHandler;
    this.cleanupExecutor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name(this.name + "-cleanup-", 0).factory());
    this.cleanupExecutor.scheduleAtFixedRate(
        this::evictIdleEntries,
        cleanupIntervalMillis,
        cleanupIntervalMillis,
        TimeUnit.MILLISECONDS);
  }

  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  public V getAndAcquire(final K key) {
    final long now = System.currentTimeMillis();
    final Entry<V> entry =
        entries.compute(
            key,
            (_key, existing) -> {
              if (existing == null) {
                return new Entry<>(creator.apply(key), now);
              }
              return existing;
            });
    entry.acquireLease(now);
    return entry.value();
  }

  public boolean release(final K key) {
    final Entry<V> entry = entries.get(key);
    if (entry == null) {
      return false;
    }
    entry.release(System.currentTimeMillis());
    return true;
  }

  public V getIfPresent(final K key) {
    final Entry<V> entry = entries.get(key);
    return entry == null ? null : entry.value();
  }

  public void invalidate(final K key) {
    final Entry<V> removed = entries.remove(key);
    if (removed == null) {
      return;
    }
    onEvict(key, removed.value());
  }

  public void invalidateAll() {
    for (final Map.Entry<K, Entry<V>> entry : entries.entrySet()) {
      if (entries.remove(entry.getKey(), entry.getValue())) {
        onEvict(entry.getKey(), entry.getValue().value());
      }
    }
  }

  public long size() {
    return entries.size();
  }

  private void evictIdleEntries() {
    final long now = System.currentTimeMillis();
    for (final Map.Entry<K, Entry<V>> entry : entries.entrySet()) {
      final Entry<V> current = entry.getValue();
      if (current.refCount() > 0) {
        continue;
      }
      if (now - current.lastAccessEpochMillis() < idleTimeoutMillis) {
        continue;
      }
      if (!entries.remove(entry.getKey(), current)) {
        continue;
      }
      onEvict(entry.getKey(), current.value());
      LOG.debug(
          "Evicted idle entry from cache '{}' key={} idle_ms={}",
          name,
          entry.getKey(),
          now - current.lastAccessEpochMillis());
    }
  }

  private void onEvict(final K key, final V value) {
    try {
      evictHandler.accept(key, value);
    } catch (Exception ex) {
      LOG.debug("Eviction handler failed for cache '{}' key={}", name, key, ex);
    }
  }

  @PreDestroy
  @Override
  public void close() {
    cleanupExecutor.shutdownNow();
    invalidateAll();
  }

  private static final class Entry<V> {
    private final V value;
    private final AtomicInteger refCount = new AtomicInteger(0);
    private volatile long lastAccessEpochMillis;

    private Entry(final V value, final long lastAccessEpochMillis) {
      this.value = value;
      this.lastAccessEpochMillis = lastAccessEpochMillis;
    }

    private V value() {
      return value;
    }

    private void acquireLease(final long now) {
      refCount.incrementAndGet();
      this.lastAccessEpochMillis = now;
    }

    private void release(final long now) {
      refCount.updateAndGet(current -> Math.max(0, current - 1));
      this.lastAccessEpochMillis = now;
    }

    private int refCount() {
      return refCount.get();
    }

    private long lastAccessEpochMillis() {
      return lastAccessEpochMillis;
    }
  }

  public static final class Builder<K, V> {
    private String name = "ref-counted-cache";
    private long idleTimeout = DEFAULT_IDLE_TIMEOUT;
    private TimeUnit idleTimeoutUnit = DEFAULT_IDLE_TIMEOUT_UNIT;
    private long cleanupInterval = DEFAULT_CLEANUP_INTERVAL;
    private TimeUnit cleanupIntervalUnit = DEFAULT_CLEANUP_INTERVAL_UNIT;
    private Function<K, V> creator;
    private BiConsumer<K, V> evictHandler = (_key, _value) -> {};

    public Builder<K, V> name(final String name) {
      this.name = name;
      return this;
    }

    public Builder<K, V> idleTimeout(final long idleTimeout, final TimeUnit unit) {
      this.idleTimeout = idleTimeout;
      this.idleTimeoutUnit =
          unit == null ? DEFAULT_IDLE_TIMEOUT_UNIT : unit;
      return this;
    }

    public Builder<K, V> cleanupInterval(final long cleanupInterval, final TimeUnit unit) {
      this.cleanupInterval = cleanupInterval;
      this.cleanupIntervalUnit =
          unit == null ? DEFAULT_CLEANUP_INTERVAL_UNIT : unit;
      return this;
    }

    public Builder<K, V> creator(final Function<K, V> creator) {
      this.creator = creator;
      return this;
    }

    public Builder<K, V> onEvict(final BiConsumer<K, V> evictHandler) {
      this.evictHandler = evictHandler == null ? (_key, _value) -> {} : evictHandler;
      return this;
    }

    public RefCountedCache<K, V> build() {
      return new RefCountedCache<>(
          name,
          idleTimeout,
          idleTimeoutUnit,
          cleanupInterval,
          cleanupIntervalUnit,
          creator,
          evictHandler);
    }
  }
}
