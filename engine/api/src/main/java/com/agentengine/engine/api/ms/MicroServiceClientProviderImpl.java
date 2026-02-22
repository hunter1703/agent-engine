package com.agentengine.engine.api.ms;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Singleton;
import java.lang.reflect.Proxy;

/**
 * Resolves {@link MicroService} dependencies, preferring a local CDI bean when available and
 * falling back to a transparent gRPC proxy for remote services.
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
    ArcContainer container = Arc.container();
    var beans = container.beanManager().getBeans(serviceClass, Any.Literal.INSTANCE);
    for (var bean : beans) {
      if (bean instanceof InjectableBean<?> injectable) {
        if (injectable.getKind() == InjectableBean.Kind.CLASS) {
          try (var localInstance = container.instance(serviceClass)) {
            if (localInstance.isAvailable()) {
              return localInstance.get();
            }
          }
        }
      }
    }

    // Fall back to a transparent gRPC proxy for remote services
    // noinspection unchecked
    return (T)
        Proxy.newProxyInstance(
            serviceClass.getClassLoader(),
            new Class<?>[] {serviceClass},
            new MicroServiceInvocationHandler(serviceClass));
  }
}
