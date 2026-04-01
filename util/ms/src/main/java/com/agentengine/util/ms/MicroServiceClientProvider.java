package com.agentengine.util.ms;

public interface MicroServiceClientProvider {

    <T> T get(Class<T> serviceClass);
}
