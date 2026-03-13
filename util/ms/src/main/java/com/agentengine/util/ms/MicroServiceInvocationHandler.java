package com.agentengine.util.ms;

import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.ms.grpc.Request;
import com.agentengine.util.ms.grpc.ServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.reactivex.rxjava3.core.Flowable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * A JDK dynamic proxy {@link InvocationHandler} that transparently forwards
 * method calls to a remote {@code @MicroService} implementation over gRPC.
 *
 * <p>
 * Blocking methods are dispatched via a server-streaming RPC and the first
 * response is returned. Methods whose return type is {@link Flowable} are
 * mapped to a lazy stream of all responses.
 */
public class MicroServiceInvocationHandler implements InvocationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(MicroServiceInvocationHandler.class);

  private final Class<?> serviceClass;
  private final ServiceGrpc.ServiceBlockingStub stub;

  public MicroServiceInvocationHandler(Class<?> serviceClass, ManagedChannel channel) {
    this.serviceClass = serviceClass;
    this.stub = ServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    LOG.info("Remote call: {}.{}()", serviceClass.getSimpleName(), method.getName());

    final String requestId = MDC.get("requestId");
    if (requestId != null) {
      LOG.info("[{}] Remote call: {}.{}()", requestId, serviceClass.getSimpleName(), method.getName());
    }

    Request request = buildRequest(method, args);
    // noinspection ReactiveStreamsUnusedPublisher
    return method.getReturnType().equals(Flowable.class) ? streamingCall(request, method) : blockingCall(request, method);
  }

  private Request buildRequest(Method method, Object[] args) {
    Request.Builder builder = Request.newBuilder().setService(serviceClass.getSimpleName()).setMethod(method.getName());

    if (args != null && args.length > 0) {
      final String requestId = MDC.get("requestId");
      LOG.info("[{}] Serializing args for {}.{}", requestId, serviceClass.getSimpleName(), method.getName());
      final long start = System.currentTimeMillis();
      final String json = JsonUtils.toJson(args);
      final long end = System.currentTimeMillis();
      LOG.info("[{}] Serialization took {}ms, payload size: {}", requestId, (end - start), json.length());
      builder.setPayload(ByteString.copyFromUtf8(json));
    }
    return builder.build();
  }

  private Object blockingCall(Request request, Method method) {
    final String requestId = MDC.get("requestId");
    LOG.info("[{}] Initiating gRPC blocking call for {}.{}", requestId, serviceClass.getSimpleName(), method.getName());
    var responseIterator = stub.execute(request);
    LOG.info("[{}] gRPC call returned for {}.{}", requestId, serviceClass.getSimpleName(), method.getName());
    if (!responseIterator.hasNext()) {
      // No payload from the server — return Optional.empty() for Optional return
      // types
      if (Optional.class.isAssignableFrom(method.getReturnType())) {
        return Optional.empty();
      }
      return null;
    }

    String payload = responseIterator.next().getPayload().toStringUtf8();
    Object result = JsonUtils.fromJson(payload, method.getGenericReturnType());

    if (result == null && Optional.class.isAssignableFrom(method.getReturnType())) {
      return Optional.empty();
    }
    return result;
  }

  private Flowable<?> streamingCall(Request request, Method method) {
    Class<?> itemType = firstTypeArgument(method.getGenericReturnType());
    return Flowable.fromIterable(() -> stub.execute(request))
        .map(response -> JsonUtils.fromJson(response.getPayload().toStringUtf8(), itemType));
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
}
