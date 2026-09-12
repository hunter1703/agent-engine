package com.agentengine.util.ms.client;

import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.context.Context;
import com.agentengine.util.ms.grpc.Request;
import com.agentengine.util.ms.grpc.Response;
import com.agentengine.util.ms.grpc.ServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.opentelemetry.api.trace.Span;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
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

  private final Class<?> serviceClass;
  private final Supplier<ManagedChannel> channelSupplier;
  private final JsonCodec jsonCodec;
  private final boolean raw;

  public MicroServiceInvocationHandler(
      Class<?> serviceClass,
      Supplier<ManagedChannel> channelSupplier,
      JsonCodec jsonCodec,
      boolean raw) {
    this.serviceClass = serviceClass;
    this.channelSupplier = channelSupplier;
    this.jsonCodec = jsonCodec;
    this.raw = raw;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    final String requestId = currentRequestId();
    LOG.debug(
        "[{}] Remote call: {}.{}()", requestId, serviceClass.getSimpleName(), method.getName());
    Span.current()
        .setAttribute("ms.remote_service", serviceClass.getSimpleName())
        .setAttribute("ms.remote_method", method.getName());

    Request request = buildRequest(method, args);
    if (Publisher.class.isAssignableFrom(method.getReturnType())) {
      return streamingCall(request, method, raw);
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
      LOG.debug(
          "[{}] Serializing args for {}.{}",
          requestId,
          serviceClass.getSimpleName(),
          method.getName());
      final long start = System.currentTimeMillis();
      final ByteString.Output out = ByteString.newOutput();
      jsonCodec.serializeBatch(args, method.getGenericParameterTypes(), out);
      final long end = System.currentTimeMillis();
      LOG.debug(
          "[{}] Serialization took {}ms, payload size: {}", requestId, (end - start), out.size());
      builder.setPayload(out.toByteString());
    }
    return builder.build();
  }

  // Stays on the blocking stub rather than the async one streamingCall() uses below: the
  // per-message thread hand-off cost that dominates a many-item stream is a one-time cost here
  // (a single response), and switching would mean this method — and every synchronous caller of
  // it — returning a Future/reactive type instead of a plain value, not just an internal swap.
  private Object blockingCall(Request request, Method method) {
    final String requestId = currentRequestId();
    LOG.debug(
        "[{}] Initiating gRPC blocking call for {}.{}",
        requestId,
        serviceClass.getSimpleName(),
        method.getName());
    final Iterator<Response> responseIterator = blockingStub().execute(request);
    LOG.debug(
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

    final Response response = responseIterator.next();
    final Type declaredType =
        CompletionStage.class.isAssignableFrom(method.getReturnType())
            ? firstTypeArgument(method.getGenericReturnType())
            : method.getGenericReturnType();
    final Object result = jsonCodec.deserialize(response.getPayload().newInput(), declaredType);

    if (result == null && Optional.class.isAssignableFrom(method.getReturnType())) {
      return Optional.empty();
    }
    return result;
  }

  // Uses the async stub instead of the blocking stub's Iterator. Both deliver messages the same
  // way up to a point: Netty's I/O thread parses the frame, then hands the decoded message to the
  // channel's callback executor (a shared, unbounded platform-thread pool — grpc-java's
  // GrpcUtil.SHARED_CHANNEL_EXECUTOR, not virtual threads) to actually invoke onMessage()/onNext().
  // That hand-off exists so a slow callback can't stall Netty's read loop.
  //
  // Where they differ: the blocking stub's Iterator has a third party involved — its onMessage()
  // just enqueues the item, and a separate application thread (parked in hasNext()/next()) has to
  // be woken to consume it. That wake-up is a real cost paid once per item, which dominates total
  // latency for a many-small-messages stream. Below, onNext() runs the consumption (deserializing
  // into flatMapIterable) inline, on that same callback-executor thread — no second party to hand
  // off to. It also means no application thread is reserved for the stream's duration: the calling
  // thread returns as soon as this method sets the call up, and work only happens in short bursts
  // on the callback executor as messages actually arrive.
  //
  // Each Response payload is a JSON-encoded batch, not a single item — flatMapIterable unpacks
  // it back into the individual-item Flowable callers expect.
  private Flowable<?> streamingCall(Request request, Method method, boolean raw) {
    final Type declaredItemType = firstTypeArgument(method.getGenericReturnType());
    final Span span = Span.current();
    final AtomicLong itemCount = new AtomicLong();
    final AtomicLong batchCount = new AtomicLong();
    return Flowable.<Response>create(
            emitter ->
                nonBlockingStub()
                    .execute(
                        request,
                        new ClientResponseObserver<Request, Response>() {
                          @Override
                          public void beforeStart(
                              final ClientCallStreamObserver<Request> requestStream) {
                            // Propagates a downstream cancel (e.g. the REST caller disconnecting)
                            // to the gRPC call — otherwise it keeps streaming for nobody.
                            emitter.setCancellable(() -> requestStream.cancel(null, null));
                          }

                          @Override
                          public void onNext(final Response response) {
                            emitter.onNext(response);
                          }

                          @Override
                          public void onError(final Throwable throwable) {
                            emitter.onError(throwable);
                          }

                          @Override
                          public void onCompleted() {
                            emitter.onComplete();
                          }
                        }),
            BackpressureStrategy.BUFFER)
        .flatMapIterable(
            response -> {
              batchCount.incrementAndGet();
              final List<?> batch =
                  raw
                      ? jsonCodec.splitNdjson(response.getPayload().toByteArray())
                      : jsonCodec.deserializeBatchNdjson(
                          response.getPayload().newInput(), declaredItemType);
              itemCount.addAndGet(batch.size());
              return batch;
            })
        .doFinally(
            () ->
                span.setAttribute("ms.item_count", itemCount.get())
                    .setAttribute("ms.batch_count", batchCount.get()));
  }

  private ServiceGrpc.ServiceBlockingStub blockingStub() {
    return ServiceGrpc.newBlockingStub(channelSupplier.get());
  }

  private ServiceGrpc.ServiceStub nonBlockingStub() {
    return ServiceGrpc.newStub(channelSupplier.get());
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

  /**
   * Returns the first type argument of a generic type, or {@code Object.class} if unavailable.
   * Preserves the full {@link Type} (not reduced to a raw {@link Class}) so this matches {@link
   * GRPCServerImpl}'s own element-type resolution for a streaming return -- collapsing a
   * parameterized argument (e.g. {@code Parent<InnerParent<String>>}) down to {@code Object.class}
   * here while the server serializes against the fully-resolved type is exactly the client/server
   * declared-type mismatch that default typing no longer papers over.
   */
  private static Type firstTypeArgument(Type type) {
    if (type instanceof ParameterizedType parameterizedType) {
      return parameterizedType.getActualTypeArguments()[0];
    }
    return Object.class;
  }
}
