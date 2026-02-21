package com.agentengine.engine.utils;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class LazyLoader<T> {
  private volatile T instance;
  private final Lock lock = new ReentrantLock();
  private final Supplier<T> provider;

  public LazyLoader(Supplier<T> provider) {
    this.provider = provider;
  }

  public T getInstance() {
    if (instance == null) {
      lock.lock();
      try {
        if (instance == null) {
          instance = provider.get();
        }
      } finally {
        lock.unlock();
      }
    }
    return instance;
  }
}
