package com.agentengine.engine.utils;

import java.util.function.Supplier;

public class LazyLoader<T> {
  private volatile T instance;
  private final Supplier<T> provider;

  public LazyLoader(Supplier<T> provider) {
    this.provider = provider;
  }

  public T getInstance() {
    if (instance == null) {
      synchronized (this) {
        if (instance == null) {
          instance = provider.get();
        }
      }
    }
    return instance;
  }
}
