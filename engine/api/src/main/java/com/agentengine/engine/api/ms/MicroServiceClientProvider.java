package com.agentengine.engine.api.ms;

public interface MicroServiceClientProvider {

  <T> T get(Class<T> serviceClass);
}
