package com.agentengine.util.ms.client;

import com.agentengine.util.mongodb.infra.InfraConfigService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Unremovable;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@link MicroService} dependencies, preferring a local CDI bean when available and
 * falling back to a transparent gRPC proxy for remote services.
 *
 * <p>Channels are cached per service class to avoid leaking gRPC connections. All channels are shut
 * down gracefully on application shutdown via {@link PreDestroy}.
 */
@Singleton
@Unremovable
public class MicroServiceClientProviderImpl implements MicroServiceClientProvider {

  private static final Logger LOG = LoggerFactory.getLogger(MicroServiceClientProviderImpl.class);
  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 9000;

  private final ConcurrentMap<Class<?>, ManagedChannel> channels = new ConcurrentHashMap<>();
  private final InfraConfigService infraConfigService;

  @Inject
  public MicroServiceClientProviderImpl(final InfraConfigService infraConfigService) {
    this.infraConfigService = infraConfigService;
  }

  @Override
  public <T> T get(Class<T> serviceClass) {
    if (!serviceClass.isAnnotationPresent(MicroService.class)) {
      throw new IllegalArgumentException(
          serviceClass.getName() + " is not annotated with @MicroService");
    }

    // Prefer a local implementation when co-located in the same process
    ArcContainer container = Arc.container();
    final Set<Bean<?>> beans = container.beanManager().getBeans(serviceClass, Any.Literal.INSTANCE);
    for (Bean<?> bean : beans) {
      if (bean instanceof InjectableBean<?> injectable) {
        if (injectable.getKind() == InjectableBean.Kind.CLASS) {
          try (InstanceHandle<T> localInstance = container.instance(serviceClass)) {
            if (localInstance.isAvailable()) {
              return localInstance.get();
            }
          }
        }
      }
    }

    // Fall back to a transparent gRPC proxy for remote services. The channel is
    // resolved lazily on the first method invocation so that bean initialization
    // does not trigger MongoDB lookups or gRPC connections at startup.
    final Supplier<ManagedChannel> channelSupplier =
        () ->
            channels.computeIfAbsent(
                serviceClass,
                cls -> {
                  final String serverId = cls.getAnnotation(MicroService.class).value();
                  final MicroServiceInfraConfig config =
                      infraConfigService.findById(
                          MicroServiceInfraConfig.CATEGORY, MicroServiceInfraConfig.TYPE, serverId);
                  final String host = config != null ? config.getHost() : DEFAULT_HOST;
                  final int port = config != null ? config.getPort() : DEFAULT_PORT;
                  LOG.debug("Resolved endpoint for server '{}': {}:{}", serverId, host, port);
                  // dns:/// + round_robin: the Service is headless (see
                  // app-base/templates/service.yaml),
                  // so DNS resolves to every backing pod's own IP rather than one virtual
                  // ClusterIP,
                  // letting the channel hold a connection per pod and spread calls across all of
                  // them
                  // instead of pinning to whichever single pod kube-proxy would have NAT'd a plain
                  // ClusterIP connection to.
                  return ManagedChannelBuilder.forTarget("dns:///" + host + ":" + port)
                      .usePlaintext()
                      .defaultLoadBalancingPolicy("round_robin")
                      .maxInboundMessageSize(MicroServiceInfraConfig.MAX_INBOUND_MESSAGE_SIZE)
                      .keepAliveTime(30, TimeUnit.SECONDS)
                      .keepAliveTimeout(10, TimeUnit.SECONDS)
                      .keepAliveWithoutCalls(true)
                      .build();
                });

    // noinspection unchecked
    return (T)
        Proxy.newProxyInstance(
            serviceClass.getClassLoader(),
            new Class<?>[] {serviceClass},
            new MicroServiceInvocationHandler(serviceClass, channelSupplier));
  }

  @PreDestroy
  private void shutdown() {
    channels.forEach(
        (serviceClass, channel) -> {
          LOG.debug("Shutting down gRPC channel for {}", serviceClass.getSimpleName());
          channel.shutdown();
          try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
              channel.shutdownNow();
            }
          } catch (InterruptedException exception) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
          }
        });
    channels.clear();
  }
}
