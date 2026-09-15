package com.agentengine.util.common;

import java.util.function.Supplier;

public class LazyLoader<T> {
  private volatile ValueHolder<T> loadedValue;
  private final Supplier<T> valueSupplier;

  public LazyLoader(Supplier<T> valueSupplier) {
    this.valueSupplier = valueSupplier;
  }

  public T get() {
    if (loadedValue != null) {
      return loadedValue.value();
    }
    synchronized (this) {
      if (loadedValue != null) {
        return loadedValue.value();
      }
      loadedValue = new ValueHolder<>(valueSupplier.get());
    }
    return loadedValue.value();
  }

  private record ValueHolder<T>(T value) {}
  ;
}
