package com.agentengine.util.common;

import java.util.function.Supplier;

public class LazyLoader<T> {
  private volatile T loadedValue;
  private final Supplier<T> valueSupplier;

  public LazyLoader(Supplier<T> valueSupplier) {
    this.valueSupplier = valueSupplier;
  }

  public T get() {
    if (loadedValue != null) {
      return loadedValue;
    }
    synchronized (this) {
      if (loadedValue != null) {
        return loadedValue;
      }
      loadedValue = valueSupplier.get();
    }
    return loadedValue;
  }
}
