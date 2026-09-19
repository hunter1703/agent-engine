package com.agentengine.util.ms.client;

import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.InfraConfigService;
import io.grpc.ManagedChannel;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.function.Supplier;

@Singleton
@Unremovable
public class MicroServiceClientProviderImpl implements MicroServiceClientProvider {

  private final MicroServiceChannelProvider channelProvider;
  private final InfraConfigService infraConfigService;
  private final JsonCodec jsonCodec;

  @Inject
  public MicroServiceClientProviderImpl(
      final MicroServiceChannelProvider channelProvider,
      final InfraConfigService infraConfigService,
      final JsonCodec jsonCodec) {
    this.channelProvider = channelProvider;
    this.infraConfigService = infraConfigService;
    this.jsonCodec = jsonCodec;
  }

  @Override
  public <T> T get(Class<T> serviceClass) {
    return resolve(serviceClass, false);
  }

  @Override
  public <T> T getRaw(Class<T> serviceClass) {
    return resolve(serviceClass, true);
  }

  private <T> T resolve(final Class<T> serviceClass, final boolean raw) {
    if (!serviceClass.isAnnotationPresent(MicroService.class)) {
      throw new IllegalArgumentException(
          serviceClass.getName() + " is not annotated with @MicroService");
    }

    // Prefer a local implementation when co-located in the same process -- "raw" is purely a
    // wire-format optimization for the gRPC proxy path below (skip binding each JSON element to a
    // real Java type), so it's meaningless once a local bean removes gRPC/JSON entirely from the
    // call.
    final T localInstance = findLocalInstance(serviceClass);
    if (localInstance != null) {
      return localInstance;
    }

    // noinspection unchecked
    return (T)
        Proxy.newProxyInstance(
            serviceClass.getClassLoader(),
            new Class<?>[] {serviceClass},
            new MicroServiceInvocationHandler(
                serviceClass, channelSupplier(serviceClass), jsonCodec, raw));
  }

  private static <T> T findLocalInstance(final Class<T> serviceClass) {
    final ArcContainer container = Arc.container();
    final Set<Bean<?>> beans = container.beanManager().getBeans(serviceClass, Any.Literal.INSTANCE);
    for (final Bean<?> bean : beans) {
      if (bean instanceof InjectableBean<?> injectable
          && injectable.getKind() == InjectableBean.Kind.CLASS) {
        try (InstanceHandle<T> localInstance = container.instance(serviceClass)) {
          if (localInstance.isAvailable()) {
            return localInstance.get();
          }
        }
      }
    }
    return null;
  }

  // Resolved on each invocation, not at startup, so that bean initialization does not trigger
  // config lookups or gRPC connections, and each call goes to the current customer's server.
  private Supplier<ManagedChannel> channelSupplier(final Class<?> serviceClass) {
    final String service = serviceClass.getAnnotation(MicroService.class).value();
    return () ->
        channelProvider.getForService(Context.customerId().orElseThrow(), service).channel();
  }
}
