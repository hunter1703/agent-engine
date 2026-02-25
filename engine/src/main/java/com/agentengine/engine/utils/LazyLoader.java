package com.agentengine.engine.utils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class LazyLoader<T> {
  private final AtomicReference<T> instance = new AtomicReference<>();
  private final Supplier<T> provider;

  public LazyLoader(final Supplier<T> provider) {
    this.provider = Objects.requireNonNull(provider, "provider cannot be null");
  }

  public T getInstance() {
    final T cached = instance.get();
    if (cached != null) {
      return cached;
    }
    final T created = provider.get();
    if (instance.compareAndSet(null, created)) {
      return created;
    }
    return instance.get();
  }
}
