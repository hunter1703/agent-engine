package com.agentengine.util.ms.client;

public interface MicroServiceClientProvider {

    <T> T get(Class<T> serviceClass);
}
