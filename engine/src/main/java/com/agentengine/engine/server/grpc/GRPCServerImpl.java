package com.agentengine.engine.server.grpc;

import com.agentengine.engine.api.exception.ConfigurationException;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.ms.MicroService;
import com.agentengine.util.ms.grpc.Request;
import com.agentengine.util.ms.grpc.Response;
import com.agentengine.util.ms.grpc.ServiceGrpc;
import com.agentengine.engine.utils.ThreadUtils;
import com.agentengine.util.common.JsonUtils;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@GrpcService
public class GRPCServerImpl extends ServiceGrpc.ServiceImplBase {
  private static final String NAME_PREFIX = "agent-grpc-vt-";

  private static final ExecutorService EXECUTOR_SERVICE =
      ThreadUtils.newVirtualThreadExecutor(NAME_PREFIX);
  private static final Logger LOG = LoggerFactory.getLogger(GRPCServerImpl.class);

  /**
   * Two-level registry built eagerly at startup. Outer key: {@link MicroService} interface simple
   * name. Inner key: method name → resolved {@link Method}.
   */
  private Map<String, ServiceEntry> registry = new HashMap<>();

  // CDI constructor
  public GRPCServerImpl() {}

  // ── Testing Helper ────────────────────────────────────────────────────────
  public GRPCServerImpl(final List<Object> services) {
    services.forEach(
        instance -> {
          Class<?> iface = microServiceInterface(instance.getClass());
          if (iface != null) {
            this.registry.put(iface.getSimpleName(), new ServiceEntry(instance, iface));
          }
        });
  }

  @PostConstruct
  public void init() {
    LOG.info("Initializing GRPCServerImpl. Scanning beans for @MicroService via Arc...");

    ArcContainer container = Arc.container();
    this.registry =
        container.beanManager().getBeans(Object.class, Any.Literal.INSTANCE).stream()
            .filter(bean -> !bean.getBeanClass().equals(GRPCServerImpl.class))
            .filter(bean -> !bean.getBeanClass().getName().contains("GRPCServerImpl"))
            .map(
                bean ->
                    container.instance(
                        bean.getBeanClass(), bean.getQualifiers().toArray(new Annotation[0])))
            .filter(InstanceHandle::isAvailable)
            .map(
                handle -> {
                  Object instance = handle.get();
                  LOG.info("Checking instance: {}", instance.getClass().getName());
                  return instance;
                })
            .flatMap(
                instance ->
                    Optional.ofNullable(microServiceInterface(instance.getClass()))
                        .map(
                            iface -> {
                              LOG.info(
                                  "Discovered MicroService: {} implemented by {}",
                                  iface.getSimpleName(),
                                  instance.getClass().getName());
                              return Map.entry(
                                  iface.getSimpleName(), new ServiceEntry(instance, iface));
                            })
                        .stream())
            // First registration wins if multiple beans implement the same interface
            .collect(
                Collectors.toUnmodifiableMap(
                    e -> e.getKey(), e -> e.getValue(), (first, __) -> first));

    LOG.info("Registered MicroServices: {}", registry.keySet());
    registry.forEach(
        (name, entry) -> LOG.debug("  {} → methods: {}", name, entry.methods().keySet()));
  }

  @Override
  public void execute(Request request, StreamObserver<Response> responseObserver) {
    try {
      EXECUTOR_SERVICE.execute(() -> executeInternal(request, responseObserver));
    } catch (RejectedExecutionException e) {
      responseObserver.onError(
          Status.RESOURCE_EXHAUSTED
              .withDescription("gRPC virtual thread executor rejected request")
              .withCause(e)
              .asRuntimeException());
    }
  }

  private void executeInternal(Request request, StreamObserver<Response> responseObserver) {
    String serviceName = request.getService();
    String methodName = request.getMethod();
    LOG.debug("GRPCServerImpl.execute called for {}/{}", serviceName, methodName);

    ServiceEntry entry = registry.get(serviceName);
    if (entry == null) {
      LOG.error("Service not found: {}. Available: {}", serviceName, registry.keySet());
      responseObserver.onError(
          Status.NOT_FOUND
              .withDescription("Service not found: " + serviceName)
              .asRuntimeException());
      return;
    }

    Method method = entry.methods().get(methodName);
    if (method == null) {
      LOG.error("Method not found: {}/{}", serviceName, methodName);
      responseObserver.onError(
          Status.NOT_FOUND.withDescription("Method not found: " + methodName).asRuntimeException());
      return;
    }

    try {
      Object[] args = deserializeArgs(request, method);
      Object result = method.invoke(entry.bean(), args);

      // Unwrap Optional — the remote caller re-wraps it after deserialization
      if (result instanceof Optional<?> opt) {
        result = opt.orElse(null);
      }

      sendResult(result, responseObserver);

    } catch (Exception e) {
      LOG.error("Error executing {}/{}", serviceName, methodName, e);
      responseObserver.onError(rootCauseStatus(e).asRuntimeException());
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

  // ── Startup helpers
  // ───────────────────────────────────────────────────────────

  /**
   * Walks the full type hierarchy (class superclasses and interface supertypes) to find an
   * interface annotated with {@link MicroService}. Returns {@code null} if none is found.
   */
  private static Class<?> microServiceInterface(Class<?> clazz) {
    if (clazz == null || clazz == Object.class) {
      return null;
    }
    for (Class<?> iface : clazz.getInterfaces()) {
      if (iface.isAnnotationPresent(MicroService.class)) {
        return iface;
      }
      // Recurse into the interface's own supertypes (interface inheritance)
      Class<?> found = microServiceInterface(iface);
      if (found != null) {
        return found;
      }
    }
    return microServiceInterface(clazz.getSuperclass());
  }

  // ── Request handling
  // ──────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private Object[] deserializeArgs(Request request, Method method) {
    int paramCount = method.getParameterCount();
    if (paramCount == 0 || request.getPayload().isEmpty()) {
      return new Object[paramCount];
    }

    Object[] rawArgs = JsonUtils.fromJson(request.getPayload().toStringUtf8(), Object[].class);
    if (rawArgs == null) {
      return new Object[paramCount];
    }

    Class<?>[] paramTypes = method.getParameterTypes();
    Object[] typedArgs = new Object[paramCount];
    for (int i = 0; i < paramCount && i < rawArgs.length; i++) {
      if (rawArgs[i] instanceof Map<?, ?> map) {
        // noinspection unchecked
        typedArgs[i] = JsonUtils.fromJacksonMap((Map<String, Object>) map, paramTypes[i]);
      } else {
        typedArgs[i] = rawArgs[i];
      }
    }
    return typedArgs;
  }

  private void sendResult(Object result, StreamObserver<Response> observer) {
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
    } else if (result != null) {
      sendPayload(observer, result);
      observer.onCompleted();
    } else {
      observer.onCompleted();
    }
  }

  private void sendPayload(StreamObserver<Response> observer, Object value) {
    observer.onNext(
        Response.newBuilder().setPayload(ByteString.copyFromUtf8(JsonUtils.toJson(value))).build());
  }

  // ── Inner types
  // ───────────────────────────────────────────────────────────────

  /**
   * Holds a registered service bean alongside its eagerly-resolved method map.
   *
   * @param bean the CDI bean instance
   * @param methods all public methods of the service interface, keyed by name
   */
  private record ServiceEntry(Object bean, Map<String, Method> methods) {

    ServiceEntry(Object bean, Class<?> iface) {
      this(
          bean,
          Arrays.stream(iface.getMethods())
              .collect(
                  Collectors.toUnmodifiableMap(Method::getName, m -> m, (first, __) -> first)));
    }
  }
}
