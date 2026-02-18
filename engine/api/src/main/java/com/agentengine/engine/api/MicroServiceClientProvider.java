package com.agentengine.engine.api;

public interface MicroServiceClientProvider {

  <T> T get(Class<T> serviceClass);
}
