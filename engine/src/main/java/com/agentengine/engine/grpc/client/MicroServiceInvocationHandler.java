package com.agentengine.engine.grpc.client;

import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.grpc.Request;
import com.agentengine.engine.grpc.Response;
import com.agentengine.engine.grpc.ServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;

/**
 * A JDK dynamic proxy {@link InvocationHandler} that transparently forwards
 * method calls
 * to a remote {@code @MicroService} implementation over gRPC.
 *
 * <p>
 * Blocking methods are dispatched via a server-streaming RPC and the first
 * response is
 * returned. Methods whose return type is {@link Flowable} are mapped to a lazy
 * stream of
 * all responses.
 */
public class MicroServiceInvocationHandler implements InvocationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(MicroServiceInvocationHandler.class);

  /**
   * {@link Object} methods that must be handled locally, never forwarded over the
   * wire.
   */
  private static final Set<String> LOCAL_OBJECT_METHODS = Set.of("toString", "hashCode", "equals");

  private final Class<?> serviceClass;
  private final ServiceGrpc.ServiceBlockingStub stub;

  public MicroServiceInvocationHandler(Class<?> serviceClass, ManagedChannel channel) {
    this.serviceClass = serviceClass;
    this.stub = ServiceGrpc.newBlockingStub(channel);
  }

  /** Convenience constructor that connects to the default local gRPC port. */
  public MicroServiceInvocationHandler(Class<?> serviceClass) {
    this(serviceClass, ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build());
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (LOCAL_OBJECT_METHODS.contains(method.getName())) {
      return handleObjectMethod(proxy, method, args);
    }

    LOG.debug("Remote call: {}.{}()", serviceClass.getSimpleName(), method.getName());

    Request request = buildRequest(method, args);
    //noinspection ReactiveStreamsUnusedPublisher
    return method.getReturnType().equals(Flowable.class)
        ? streamingCall(request, method)
        : blockingCall(request, method);
  }

  private Request buildRequest(Method method, Object[] args) {
    Request.Builder builder = Request.newBuilder()
        .setService(serviceClass.getSimpleName())
        .setMethod(method.getName());

    if (args != null && args.length > 0) {
      builder.setPayload(ByteString.copyFromUtf8(JsonUtils.toJson(args)));
    }
    return builder.build();
  }

  private Object blockingCall(Request request, Method method) {
    var responseIterator = stub.execute(request);
    if (!responseIterator.hasNext()) {
      return null;
    }

    String payload = responseIterator.next().getPayload().toStringUtf8();
    Class<?> returnType = method.getReturnType();

    return returnType.equals(Optional.class)
        ? deserializeOptional(payload, method.getGenericReturnType())
        : JsonUtils.fromJson(payload, returnType);
  }

  private Flowable<?> streamingCall(Request request, Method method) {
    Class<?> itemType = firstTypeArgument(method.getGenericReturnType());
    return Flowable.fromIterable(() -> stub.execute(request))
        .map(response -> JsonUtils.fromJson(response.getPayload().toStringUtf8(), itemType));
  }

  private Optional<?> deserializeOptional(String payload, Type genericReturnType) {
    if (payload == null || payload.isBlank()) {
      return Optional.empty();
    }
    Class<?> typeArg = firstTypeArgument(genericReturnType);
    return Optional.ofNullable(JsonUtils.fromJson(payload, typeArg));
  }

  /**
   * Returns the first type argument of a generic type, or {@code Object.class} if
   * unavailable.
   */
  private static Class<?> firstTypeArgument(Type type) {
    if (type instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> clazz) {
      return clazz;
    }
    return Object.class;
  }

  private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
    return switch (method.getName()) {
      case "toString" -> STR."MicroServiceProxy(\{serviceClass.getSimpleName()})";
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> args != null && args.length > 0 && proxy == args[0];
      default -> throw new UnsupportedOperationException(method.getName());
    };
  }
}
