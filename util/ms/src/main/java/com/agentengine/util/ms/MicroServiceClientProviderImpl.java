package com.agentengine.util.ms;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;
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
public class MicroServiceClientProviderImpl implements MicroServiceClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MicroServiceClientProviderImpl.class);

    private final ConcurrentMap<Class<?>, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final MicroServiceEndpointResolver endpointResolver;

    @Inject
    public MicroServiceClientProviderImpl(MicroServiceEndpointResolver endpointResolver) {
        this.endpointResolver = endpointResolver;
    }

    @Override
    public <T> T get(Class<T> serviceClass) {
        if (!serviceClass.isAnnotationPresent(MicroService.class)) {
            throw new IllegalArgumentException(serviceClass.getName() + " is not annotated with @MicroService");
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

        // Fall back to a transparent gRPC proxy for remote services, reusing the
        // channel per service
        ManagedChannel channel = channels.computeIfAbsent(serviceClass, cls -> {
            MicroServiceEndpoint endpoint = endpointResolver.resolve(cls);
            return ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port())
                    .usePlaintext()
                    .build();
        });

        // noinspection unchecked
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class<?>[] {serviceClass},
                new MicroServiceInvocationHandler(serviceClass, channel));
    }

    @PreDestroy
    private void shutdown() {
        channels.forEach((serviceClass, channel) -> {
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
