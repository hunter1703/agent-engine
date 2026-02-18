package com.agentengine.engine.grpc;

import com.agentengine.engine.api.MicroService;
import com.agentengine.engine.api.utils.JsonUtils;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.quarkus.arc.All;
import io.quarkus.grpc.GrpcService;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@GrpcService
public class GrpcServiceImpl extends ServiceGrpc.ServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServiceImpl.class);

    /**
     * Two-level registry built eagerly at startup.
     * Outer key: {@link MicroService} interface simple name.
     * Inner key: method name → resolved {@link Method}.
     */
    private final Map<String, ServiceEntry> registry;

    @Inject
    public GrpcServiceImpl(@All List<Object> allBeans) {
        this.registry = allBeans.stream()
                .flatMap(bean -> Optional.ofNullable(microServiceInterface(bean.getClass()))
                        .map(iface -> Map.entry(iface.getSimpleName(), new ServiceEntry(bean, iface)))
                        .stream())
                // First registration wins if multiple beans implement the same interface
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (first, __) -> first));

        LOG.info("Registered MicroServices: {}", registry.keySet());
        registry.forEach((name, entry) -> LOG.debug("  {} → methods: {}", name, entry.methods().keySet()));
    }

  @Override
  public void execute(Request request, StreamObserver<Response> responseObserver) {
    String serviceName = request.getService();
    String methodName = request.getMethod();

    ServiceEntry entry = registry.get(serviceName);
    if (entry == null) {
      LOG.error("Service not found: {}. Available: {}", serviceName, registry.keySet());
      responseObserver.onError(Status.NOT_FOUND
          .withDescription(STR."Service not found: \{serviceName}")
          .asRuntimeException());
      return;
    }

    Method method = entry.methods().get(methodName);
    if (method == null) {
      LOG.error("Method not found: {}/{}", serviceName, methodName);
      responseObserver.onError(Status.NOT_FOUND
          .withDescription(STR."Method not found: \{methodName}")
          .asRuntimeException());
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
      responseObserver.onError(Status.INTERNAL
          .withDescription(e.getMessage())
          .withCause(e)
          .asRuntimeException());
    }
  }

    // ── Startup helpers
    // ───────────────────────────────────────────────────────────

    /**
     * Walks the full type hierarchy (class superclasses and interface supertypes)
     * to find
     * an interface annotated with {@link MicroService}. Returns {@code null} if
     * none is found.
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
            flowable.subscribe(
                    item -> sendPayload(observer, item),
                    err -> observer.onError(Status.INTERNAL
                            .withDescription(err.getMessage())
                            .withCause(err)
                            .asRuntimeException()),
                    observer::onCompleted);
        } else if (result != null) {
            sendPayload(observer, result);
            observer.onCompleted();
        } else {
            observer.onCompleted();
        }
    }

    private void sendPayload(StreamObserver<Response> observer, Object value) {
        observer.onNext(Response.newBuilder()
                .setPayload(ByteString.copyFromUtf8(JsonUtils.toJson(value)))
                .build());
    }

    // ── Inner types
    // ───────────────────────────────────────────────────────────────

    /**
     * Holds a registered service bean alongside its eagerly-resolved method map.
     *
     * @param bean    the CDI bean instance
     * @param methods all public methods of the service interface, keyed by name
     */
    private record ServiceEntry(Object bean, Map<String, Method> methods) {

        ServiceEntry(Object bean, Class<?> iface) {
            this(bean, Arrays.stream(iface.getMethods())
                    .collect(Collectors.toUnmodifiableMap(Method::getName, m -> m, (first, __) -> first)));
        }
    }
}
