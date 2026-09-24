package com.agentengine.util.common;

import java.util.concurrent.atomic.AtomicInteger;

public final class RefCounted<V> implements AutoCloseable {

  private final V value;
  private final AtomicInteger leaseCount = new AtomicInteger(0);
  private volatile long lastAccessEpochMillis = System.currentTimeMillis();

  public RefCounted(final V value) {
    this.value = value;
  }

  public RefCounted<V> acquire() {
    leaseCount.incrementAndGet();
    lastAccessEpochMillis = System.currentTimeMillis();
    return this;
  }

  public void release() {
    leaseCount.updateAndGet(current -> Math.max(0, current - 1));
    lastAccessEpochMillis = System.currentTimeMillis();
  }

  @Override
  public void close() {
    release();
  }

  public V value() {
    return value;
  }

  public int leaseCount() {
    return leaseCount.get();
  }

  public boolean isIdle(final long idleTimeoutMillis) {
    return leaseCount.get() <= 0
        && System.currentTimeMillis() - lastAccessEpochMillis >= idleTimeoutMillis;
  }
}
