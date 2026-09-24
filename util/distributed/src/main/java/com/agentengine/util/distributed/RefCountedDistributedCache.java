package com.agentengine.util.distributed;

import com.agentengine.util.common.RefCounted;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public final class RefCountedDistributedCache<V> extends DistributedCache<RefCounted<V>> {

  private RefCountedDistributedCache(final Builder<V> builder) {
    super(builder);
  }

  public <Value extends V> RefCounted<Value> getOrLoad(
      final String key, final Function<String, Value> loader) {
    //noinspection unchecked
    return (RefCounted<Value>)
        localCache.get(
            namespacedKey(key),
            namespacedKey -> new RefCounted<>(loader.apply(unwrapKey(namespacedKey))));
  }

  public static final class Builder<V> extends DistributedCache.Builder<RefCounted<V>> {
    private static final long DEFAULT_IDLE_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long DEFAULT_REAP_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private long idleTimeoutMillis = DEFAULT_IDLE_TIMEOUT_MILLIS;
    private long reapIntervalMillis = DEFAULT_REAP_INTERVAL_MILLIS;

    public Builder(final String cacheName, final DistributedCacheManager cacheManager) {
      super(cacheName, cacheManager);
    }

    public RefCountedDistributedCache.Builder<V> tags(final Set<CacheTag> tags) {
      super.tags(tags);
      return this;
    }

    public Builder<V> onEvict(final Consumer<V> onEvict) {
      removalListener(entry -> onEvict.accept(entry.value()));
      return this;
    }

    public Builder<V> idleTimeout(final long idleTimeout, final TimeUnit unit) {
      this.idleTimeoutMillis = unit.toMillis(idleTimeout);
      return this;
    }

    public Builder<V> cleanupInterval(final long cleanupInterval, final TimeUnit unit) {
      this.reapIntervalMillis = unit.toMillis(cleanupInterval);
      return this;
    }

    @Override
    public RefCountedDistributedCache<V> build() {
      validate();
      reapWhen(entry -> entry.isIdle(idleTimeoutMillis), reapIntervalMillis, TimeUnit.MILLISECONDS);
      return new RefCountedDistributedCache<>(this);
    }
  }
}
