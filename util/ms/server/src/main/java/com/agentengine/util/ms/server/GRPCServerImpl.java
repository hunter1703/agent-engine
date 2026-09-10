package com.agentengine.util.ms.server;

import com.agentengine.util.common.Defaults;
import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.context.Context;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.ms.client.MicroService;
import com.agentengine.util.ms.client.MicroServiceMethod;
import com.agentengine.util.ms.grpc.Request;
import com.agentengine.util.ms.grpc.Response;
import com.agentengine.util.ms.grpc.ServiceGrpc;
import com.google.common.base.Throwables;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.grpc.GrpcService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@GrpcService
public class GRPCServerImpl extends ServiceGrpc.ServiceImplBase {
  private static final String NAME_PREFIX = "agent-grpc-vt-";
  private static final ExecutorService EXECUTOR_SERVICE =
      Executors.newThreadPerTaskExecutor(
          Thread.ofVirtual().name(GRPCServerImpl.NAME_PREFIX, 0).factory());
  private static final Logger LOG = LoggerFactory.getLogger(GRPCServerImpl.class);

  private Map<String, ServiceEntry> registry = new HashMap<>();
  private final JsonCodec jsonCodec;

  @Inject
  public GRPCServerImpl(final JsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
  }

  public GRPCServerImpl(final List<Object> services, final JsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
    services.forEach(
        instance -> {
          final Class<?> iface = microServiceInterface(instance.getClass());
          if (iface != null) {
            this.registry.put(iface.getSimpleName(), new ServiceEntry(instance, iface));
          }
        });
  }

  @PostConstruct
  public void init() {
    final ArcContainer container = Arc.container();
    this.registry =
        container.beanManager().getBeans(Object.class, Any.Literal.INSTANCE).stream()
            .filter(bean -> !bean.getBeanClass().getName().contains("GRPCServerImpl"))
            .flatMap(
                bean ->
                    Optional.ofNullable(microServiceInterface(bean.getBeanClass()))
                        .map(
                            iface -> {
                              // Every discovered MicroService bean is @Singleton, a
                              // container-managed scope whose lifecycle close() doesn't affect —
                              // it only matters for @Dependent, where it triggers destruction. Safe
                              // to close here; the instance stays valid for as long as this
                              // registry holds onto it.
                              try (InstanceHandle<?> handle =
                                  container.instance(
                                      bean.getBeanClass(),
                                      bean.getQualifiers().toArray(new Annotation[0]))) {
                                if (!handle.isAvailable()) {
                                  return null;
                                }
                                final Object instance = handle.get();
                                LOG.info(
                                    "Discovered MicroService: {} implemented by {}",
                                    iface.getSimpleName(),
                                    instance.getClass().getName());
                                return Map.entry(
                                    iface.getSimpleName(), new ServiceEntry(instance, iface));
                              }
                            })
                        .stream())
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, Map.Entry::getValue, (first, __) -> first));

    LOG.info("Registered MicroServices: {}", registry.keySet());
    registry.forEach(
        (name, entry) -> LOG.debug("  {} -> methods: {}", name, entry.methods().keySet()));
  }

  @Override
  public void execute(final Request request, final StreamObserver<Response> responseObserver) {
    final SerialDisposable disposableProxy = new SerialDisposable();
    if (responseObserver instanceof ServerCallStreamObserver<Response> serverCallObserver) {
      serverCallObserver.setOnCancelHandler(disposableProxy::dispose);
    }
    dispatch(request, responseObserver, disposableProxy);
  }

  private void dispatch(
      final Request request,
      final StreamObserver<Response> responseObserver,
      final SerialDisposable disposableProxy) {
    // Capture the current span/context before crossing the manual dispatch-to-virtual-thread
    // boundary below — otherwise Span.current() inside dispatch() would see whatever (or
    // nothing) is ambient on that fresh virtual thread, not the span this call arrived on.
    final io.opentelemetry.context.Context otelContext = io.opentelemetry.context.Context.current();
    EXECUTOR_SERVICE.execute(
        otelContext.wrap(
            () -> {
              final Context context =
                  jsonCodec.deserialize(request.getContext().toStringUtf8(), Context.class);
              if (context != null) {
                context.run(() -> executeInternal(request, responseObserver, disposableProxy));
              } else {
                executeInternal(request, responseObserver, disposableProxy);
              }
            }));
  }

  private void executeInternal(
      final Request request,
      final StreamObserver<Response> responseObserver,
      final SerialDisposable disposableProxy) {
    final String serviceName = request.getService();
    final String methodName = request.getMethod();
    LOG.debug("GRPCServerImpl.execute called for {}/{}", serviceName, methodName);
    final Span span = Span.current();
    span.setAttribute("ms.service", serviceName).setAttribute("ms.method", methodName);

    final ServiceEntry entry = registry.get(serviceName);
    if (entry == null) {
      LOG.error("Service not found: {}. Available: {}", serviceName, registry.keySet());
      responseObserver.onError(
          Status.NOT_FOUND
              .withDescription("Service not found: " + serviceName)
              .asRuntimeException());
      return;
    }

    final ServiceMethod serviceMethod = entry.methods().get(methodName);
    if (serviceMethod == null) {
      LOG.error(
          "Method not found: {}/{} (available: {})",
          serviceName,
          methodName,
          entry.methods().keySet());
      responseObserver.onError(
          Status.NOT_FOUND.withDescription("Method not found: " + methodName).asRuntimeException());
      return;
    }

    final Method method = serviceMethod.method();
    try {
      final Object[] args = deserializeArgs(request, method);
      final Object result = method.invoke(entry.bean(), args);
      if (result instanceof Flowable<?> flowable) {
        LOG.debug("Subscribing to Flowable result...");
        final AtomicLong itemCount = new AtomicLong();
        final AtomicLong batchCount = new AtomicLong();
        // The cancel handler itself is already registered on disposable back in execute(); this
        // just supplies the real subscription. set() disposes it
        // immediately instead of leaking it if a cancel already landed in the meantime.
        Disposable subscription =
            flowable
                .buffer(
                    serviceMethod.streamingBatchFlushIntervalMs(),
                    TimeUnit.MILLISECONDS,
                    serviceMethod.streamingBatchSize())
                .filter(batch -> !batch.isEmpty())
                .subscribe(
                    batch -> {
                      itemCount.addAndGet(batch.size());
                      batchCount.incrementAndGet();
                      final Type returnType = method.getGenericReturnType();
                      final Type elementType =
                          returnType instanceof ParameterizedType
                              ? ((ParameterizedType) returnType).getActualTypeArguments()[0]
                              : Object.class;
                      send(responseObserver, jsonCodec.serializeBatch(batch, elementType));
                    },
                    err -> {
                      LOG.error("Flowable error", err);
                      span.setAttribute("ms.item_count", itemCount.get())
                          .setAttribute("ms.batch_count", batchCount.get());
                      responseObserver.onError(rootCauseStatus(err).asRuntimeException());
                    },
                    () -> {
                      span.setAttribute("ms.item_count", itemCount.get())
                          .setAttribute("ms.batch_count", batchCount.get());
                      responseObserver.onCompleted();
                    });
        disposableProxy.set(subscription);
        return;
      }
      if (result != null) {
        send(responseObserver, jsonCodec.serialize(result, method.getGenericReturnType()));
      }
      responseObserver.onCompleted();
    } catch (final Exception exception) {
      LOG.error("Error executing {}/{}", serviceName, methodName, exception);
      responseObserver.onError(rootCauseStatus(exception).asRuntimeException());
    }
  }

  private void send(final StreamObserver<Response> responseObserver, final String json) {
    responseObserver.onNext(
        Response.newBuilder().setPayload(ByteString.copyFromUtf8(json)).build());
  }

  private static Status rootCauseStatus(final Throwable throwable) {
    final Throwable cause = Throwables.getRootCause(throwable);
    return switch (cause) {
      case AssetNotFoundException _ ->
          Status.NOT_FOUND.withDescription(cause.getMessage()).withCause(cause);
      case DuplicateAssetException _ ->
          Status.ALREADY_EXISTS.withDescription(cause.getMessage()).withCause(cause);
      case IllegalArgumentException _, ConfigurationException _ ->
          Status.INVALID_ARGUMENT.withDescription(cause.getMessage()).withCause(cause);
      default -> Status.INTERNAL.withDescription(cause.getMessage()).withCause(cause);
    };
  }

  private static Class<?> microServiceInterface(final Class<?> clazz) {
    if (clazz == null || clazz == Object.class) {
      return null;
    }
    for (final Class<?> iface : clazz.getInterfaces()) {
      if (iface.isAnnotationPresent(MicroService.class)) {
        return iface;
      }
      final Class<?> found = microServiceInterface(iface);
      if (found != null) {
        return found;
      }
    }
    return microServiceInterface(clazz.getSuperclass());
  }

  private Object[] deserializeArgs(final Request request, final Method method) {
    final int paramCount = method.getParameterCount();
    if (paramCount == 0 || request.getPayload().isEmpty()) {
      return new Object[paramCount];
    }
    LOG.debug(
        "Deserializing args for {} with payload: {}", method, request.getPayload().toStringUtf8());
    final Object[] typedArgs =
        jsonCodec.deserializeBatch(
            request.getPayload().toStringUtf8(), method.getGenericParameterTypes());
    if (typedArgs == null) {
      return new Object[paramCount];
    }
    return typedArgs;
  }

  private static String methodKey(final Method method) {
    return method.getName()
        + "#"
        + Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(","));
  }

  private static ServiceMethod serviceMethod(final Method method) {
    final MicroServiceMethod override = method.getAnnotation(MicroServiceMethod.class);
    return override != null
        ? new ServiceMethod(
            method, override.streamingBatchSize(), override.streamingBatchFlushIntervalMs())
        : new ServiceMethod(
            method, Defaults.STREAMING_BATCH_SIZE, Defaults.STREAMING_BATCH_FLUSH_INTERVAL_MS);
  }

  private record ServiceEntry(Object bean, Map<String, ServiceMethod> methods) {
    private ServiceEntry(final Object bean, final Class<?> iface) {
      this(
          bean,
          Arrays.stream(iface.getMethods())
              .collect(
                  Collectors.toUnmodifiableMap(
                      GRPCServerImpl::methodKey,
                      GRPCServerImpl::serviceMethod,
                      (first, __) -> first)));
    }
  }

  private record ServiceMethod(
      Method method, int streamingBatchSize, long streamingBatchFlushIntervalMs) {}
}
