package com.agentengine.engine.grpc.client;

import com.agentengine.engine.api.MicroService;
import com.agentengine.engine.api.MicroServiceClientProvider;
import io.quarkus.arc.Arc;
import jakarta.inject.Singleton;

import java.lang.reflect.Proxy;

/**
 * Resolves {@link MicroService} dependencies, preferring a local CDI bean when
 * available and falling back to a transparent gRPC proxy for remote services.
 */
@Singleton
public class MicroServiceClientProviderImpl implements MicroServiceClientProvider {

  @Override
  public <T> T get(Class<T> serviceClass) {
    if (!serviceClass.isAnnotationPresent(MicroService.class)) {
      throw new IllegalArgumentException(
              STR."\{serviceClass.getName()} is not annotated with @MicroService");
    }

    // Prefer a local implementation when co-located in the same process
    try (var localInstance = Arc.container().instance(serviceClass)) {
      if (localInstance.isAvailable()) {
        return localInstance.get();
      }
    }

    // Fall back to a transparent gRPC proxy for remote services
    // noinspection unchecked
    return (T) Proxy.newProxyInstance(
        serviceClass.getClassLoader(),
        new Class<?>[] { serviceClass },
        new MicroServiceInvocationHandler(serviceClass));
  }
}
