package com.agentengine.util.ms.client;

import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.context.Context;
import com.agentengine.util.ms.grpc.Request;
import com.agentengine.util.ms.grpc.Response;
import com.agentengine.util.ms.grpc.ServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JDK dynamic proxy {@link InvocationHandler} that transparently forwards method calls to a
 * remote {@code @MicroService} implementation over gRPC.
 *
 * <p>Blocking methods are dispatched via a server-streaming RPC and the first response is returned.
 * Methods whose return type is a {@link Publisher} (including {@link Flowable}) are mapped to a
 * lazy stream of all responses.
 */
public class MicroServiceInvocationHandler implements InvocationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(MicroServiceInvocationHandler.class);
  private static final ExecutorService STREAM_EXECUTOR =
      Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("grpc-stream-", 0).factory());

  private final Class<?> serviceClass;
  private final Supplier<ManagedChannel> channelSupplier;

  public MicroServiceInvocationHandler(
      Class<?> serviceClass, Supplier<ManagedChannel> channelSupplier) {
    this.serviceClass = serviceClass;
    this.channelSupplier = channelSupplier;
  }

  private ServiceGrpc.ServiceBlockingStub stub() {
    return ServiceGrpc.newBlockingStub(channelSupplier.get());
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    final String requestId = currentRequestId();
    LOG.info(
        "[{}] Remote call: {}.{}()", requestId, serviceClass.getSimpleName(), method.getName());

    Request request = buildRequest(method, args);
    if (Publisher.class.isAssignableFrom(method.getReturnType())) {
      return streamingCall(request, method);
    }
    final Object result = blockingCall(request, method);
    return method.getReturnType().equals(CompletionStage.class)
        ? CompletableFuture.completedFuture(result)
        : result;
  }

  private Request buildRequest(Method method, Object[] args) {
    final String requestId = currentRequestId();
    Request.Builder builder =
        Request.newBuilder().setService(serviceClass.getSimpleName()).setMethod(methodKey(method));
    Context.current()
        .ifPresent(
            context -> builder.setContext(ByteString.copyFromUtf8(JsonUtils.toJson(context))));

    if (args != null && args.length > 0) {
      LOG.info(
          "[{}] Serializing args for {}.{}",
          requestId,
          serviceClass.getSimpleName(),
          method.getName());
      final long start = System.currentTimeMillis();
      final String json = JsonUtils.toJson(args);
      final long end = System.currentTimeMillis();
      LOG.info("[{}] Serialization took {}ms, payload : {}", requestId, (end - start), json);
      builder.setPayload(ByteString.copyFromUtf8(json));
    }
    return builder.build();
  }

  private Object blockingCall(Request request, Method method) {
    final String requestId = currentRequestId();
    LOG.info(
        "[{}] Initiating gRPC blocking call for {}.{}",
        requestId,
        serviceClass.getSimpleName(),
        method.getName());
    final Iterator<Response> responseIterator = stub().execute(request);
    LOG.info(
        "[{}] gRPC call returned for {}.{}",
        requestId,
        serviceClass.getSimpleName(),
        method.getName());
    if (!responseIterator.hasNext()) {
      // No payload from the server — return Optional.empty() for Optional return
      // types
      if (Optional.class.isAssignableFrom(method.getReturnType())) {
        return Optional.empty();
      }
      return null;
    }

    final String payload = responseIterator.next().getPayload().toStringUtf8();
    final Type deserializationType =
        CompletionStage.class.isAssignableFrom(method.getReturnType())
            ? firstTypeArgument(method.getGenericReturnType())
            : method.getGenericReturnType();
    Object result = JsonUtils.fromJson(payload, deserializationType, true);

    if (result == null && Optional.class.isAssignableFrom(method.getReturnType())) {
      return Optional.empty();
    }
    return result;
  }

  private Flowable<?> streamingCall(Request request, Method method) {
    final Class<?> itemType = firstTypeArgument(method.getGenericReturnType());
    return Flowable.fromIterable(() -> stub().execute(request))
        // gRPC's blocking response iterator must never be consumed on a Vert.x event-loop thread.
        .subscribeOn(Schedulers.from(STREAM_EXECUTOR))
        .map(response -> JsonUtils.fromJson(response.getPayload().toStringUtf8(), itemType, true));
  }

  private static String currentRequestId() {
    return Context.current().map(Context::requestId).orElse(null);
  }

  private static String methodKey(final Method method) {
    return method.getName()
        + "#"
        + Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(","));
  }

  /** Returns the first type argument of a generic type, or {@code Object.class} if unavailable. */
  private static Class<?> firstTypeArgument(Type type) {
    if (type instanceof ParameterizedType parameterizedType
        && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> clazz) {
      return clazz;
    }
    return Object.class;
  }
}
