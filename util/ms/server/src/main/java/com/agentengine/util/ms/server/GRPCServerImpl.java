package com.agentengine.util.ms.server;

import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.ms.client.MicroService;
import com.agentengine.util.ms.grpc.Request;
import com.agentengine.util.ms.grpc.Response;
import com.agentengine.util.ms.grpc.ServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.grpc.GrpcService;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@GrpcService
public class GRPCServerImpl extends ServiceGrpc.ServiceImplBase {
  private static final String NAME_PREFIX = "agent-grpc-vt-";
  private static final ExecutorService EXECUTOR_SERVICE = newVirtualThreadExecutor(NAME_PREFIX);
  private static final Logger LOG = LoggerFactory.getLogger(GRPCServerImpl.class);

  private Map<String, ServiceEntry> registry = new HashMap<>();

  public GRPCServerImpl() {}

  public GRPCServerImpl(final List<Object> services) {
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
            .filter(bean -> !bean.getBeanClass().equals(GRPCServerImpl.class))
            .filter(bean -> !bean.getBeanClass().getName().contains("GRPCServerImpl"))
            .flatMap(
                bean ->
                    Optional.ofNullable(microServiceInterface(bean.getBeanClass()))
                        .map(
                            iface -> {
                              final InstanceHandle<?> handle =
                                  container.instance(
                                      bean.getBeanClass(),
                                      bean.getQualifiers().toArray(new Annotation[0]));
                              if (!handle.isAvailable()) return null;
                              final Object instance = handle.get();
                              LOG.info(
                                  "Discovered MicroService: {} implemented by {}",
                                  iface.getSimpleName(),
                                  instance.getClass().getName());
                              return Map.entry(
                                  iface.getSimpleName(), new ServiceEntry(instance, iface));
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
    try {
      EXECUTOR_SERVICE.execute(() -> executeInternal(request, responseObserver));
    } catch (final RejectedExecutionException exception) {
      responseObserver.onError(
          Status.RESOURCE_EXHAUSTED
              .withDescription("gRPC virtual thread executor rejected request")
              .withCause(exception)
              .asRuntimeException());
    }
  }

  private void executeInternal(
      final Request request, final StreamObserver<Response> responseObserver) {
    final String serviceName = request.getService();
    final String methodName = request.getMethod();
    LOG.debug("GRPCServerImpl.execute called for {}/{}", serviceName, methodName);

    final ServiceEntry entry = registry.get(serviceName);
    if (entry == null) {
      LOG.error("Service not found: {}. Available: {}", serviceName, registry.keySet());
      responseObserver.onError(
          Status.NOT_FOUND
              .withDescription("Service not found: " + serviceName)
              .asRuntimeException());
      return;
    }

    final Method method = entry.methods().get(methodName);
    if (method == null) {
      LOG.error(
          "Method not found: {}/{} (available: {})",
          serviceName,
          methodName,
          entry.methods().keySet());
      responseObserver.onError(
          Status.NOT_FOUND.withDescription("Method not found: " + methodName).asRuntimeException());
      return;
    }

    try {
      final Object[] args = deserializeArgs(request, method);
      Object result = method.invoke(entry.bean(), args);
      if (result instanceof Optional<?> optional) {
        result = optional.orElse(null);
      }
      if (result instanceof CompletionStage<?> stage) {
        stage.whenComplete(
            (value, ex) -> {
              if (ex != null) {
                LOG.error("Error executing {}/{}", serviceName, methodName, ex);
                responseObserver.onError(rootCauseStatus(ex).asRuntimeException());
              } else {
                sendResult(value, responseObserver);
              }
            });
        return;
      }
      sendResult(result, responseObserver);
    } catch (final Exception exception) {
      LOG.error("Error executing {}/{}", serviceName, methodName, exception);
      responseObserver.onError(rootCauseStatus(exception).asRuntimeException());
    }
  }

  private static Throwable rootCause(final Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private static Status rootCauseStatus(final Throwable throwable) {
    final Throwable cause = rootCause(throwable);
    if (cause instanceof AssetNotFoundException) {
      return Status.NOT_FOUND.withDescription(cause.getMessage()).withCause(cause);
    }
    if (cause instanceof DuplicateAssetException) {
      return Status.ALREADY_EXISTS.withDescription(cause.getMessage()).withCause(cause);
    }
    if (cause instanceof IllegalArgumentException || cause instanceof ConfigurationException) {
      return Status.INVALID_ARGUMENT.withDescription(cause.getMessage()).withCause(cause);
    }
    return Status.INTERNAL.withDescription(cause.getMessage()).withCause(cause);
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

    final Object[] rawArgs =
        JsonUtils.fromJson(request.getPayload().toStringUtf8(), Object[].class);
    if (rawArgs == null) {
      return new Object[paramCount];
    }

    final Class<?>[] paramTypes = method.getParameterTypes();
    final Object[] typedArgs = new Object[paramCount];
    for (int index = 0; index < paramCount && index < rawArgs.length; index++) {
      if (rawArgs[index] instanceof Map<?, ?> argumentMap) {
        typedArgs[index] = JsonUtils.fromMap((Map<String, Object>) argumentMap, paramTypes[index]);
      } else {
        typedArgs[index] = rawArgs[index];
      }
    }
    return typedArgs;
  }

  private void sendResult(final Object result, final StreamObserver<Response> observer) {
    if (result instanceof Flowable<?> flowable) {
      LOG.debug("Subscribing to Flowable result...");
      flowable.subscribe(
          item -> {
            LOG.debug("Sending Flowable item: {}", item.getClass().getSimpleName());
            sendPayload(observer, item);
          },
          err -> {
            LOG.error("Flowable error", err);
            observer.onError(rootCauseStatus(err).asRuntimeException());
          },
          () -> {
            LOG.debug("Flowable complete");
            observer.onCompleted();
          });
      return;
    }
    if (result != null) {
      sendPayload(observer, result);
    }
    observer.onCompleted();
  }

  private void sendPayload(final StreamObserver<Response> observer, final Object value) {
    observer.onNext(
        Response.newBuilder()
            .setPayload(ByteString.copyFromUtf8(JsonUtils.toJson(value, true)))
            .build());
  }

  private static ExecutorService newVirtualThreadExecutor(final String namePrefix) {
    final ThreadFactory factory = Thread.ofVirtual().name(namePrefix, 0).factory();
    return Executors.newThreadPerTaskExecutor(factory);
  }

  private static String methodKey(final Method method) {
    return method.getName()
        + "#"
        + Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(","));
  }

  private record ServiceEntry(Object bean, Map<String, Method> methods) {
    private ServiceEntry(final Object bean, final Class<?> iface) {
      this(
          bean,
          Arrays.stream(iface.getMethods())
              .collect(
                  Collectors.toUnmodifiableMap(
                      GRPCServerImpl::methodKey, m -> m, (first, __) -> first)));
    }
  }
}
