package com.agentengine.util.ms.server;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.Gradient2Limit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounds how many microservice calls this server processes at once, rejecting the excess with
 * {@code RESOURCE_EXHAUSTED}. This is a {@link ServerInterceptor} rather than a method-scoped guard
 * (e.g. MicroProfile Fault Tolerance's {@code @Bulkhead}): dispatch returns as soon as it
 * subscribes to a streaming call's {@link io.reactivex.rxjava3.core.Flowable}, long before the
 * stream finishes, so a method-scoped guard would release its permit immediately instead of for the
 * stream's real lifetime.
 *
 * <p>The limit is adaptive (Netflix concurrency-limits' Gradient2 algorithm) rather than a fixed
 * count: a static number has to be hand-tuned per deployment and can't distinguish between methods
 * with very different loads. Gradient2 watches call latency and grows/shrinks the limit to match
 * what the pod can currently sustain, with no number to guess. It's wrapped in {@link
 * WindowedLimit} so one outlier RTT (a GC pause, a momentary CPU throttle) can't itself move the
 * limit — a window of samples is aggregated into a single representative RTT before Gradient2 ever
 * sees it.
 *
 * <p>Netflix's own {@code ConcurrencyLimitServerInterceptor}/{@code GrpcServerLimiterBuilder}
 * aren't used directly: that interceptor only limits RPCs where both sides send exactly one
 * message, and every call here — blocking or streaming — goes through the single server-streaming
 * {@code execute} RPC, so that gate would never engage. This wraps the same {@link Limiter} core
 * the library ships instead, mirroring that interceptor's own status-to-outcome mapping: every
 * outcome counts as a normal latency sample except a blown deadline or a client cancel, which count
 * as congestion.
 */
@Singleton
// TEMPORARILY DISABLED for load testing without adaptive concurrency limiting — re-enable
// (uncomment @GlobalInterceptor) before merging/deploying anywhere else.
// @GlobalInterceptor
public class ConcurrencyLimitingServerInterceptor implements ServerInterceptor {
  private static final Status LIMIT_EXCEEDED_STATUS =
      Status.RESOURCE_EXHAUSTED.withDescription("Server concurrency limit reached");

  private final Limiter<Object> limiter =
      SimpleLimiter.newBuilder()
          .limit(WindowedLimit.newBuilder().build(Gradient2Limit.newDefault()))
          .build();

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final Optional<Limiter.Listener> acquired = limiter.acquire(null);
    if (acquired.isEmpty()) {
      call.close(LIMIT_EXCEEDED_STATUS, new Metadata());
      return new ServerCall.Listener<>() {};
    }

    final Limiter.Listener limiterListener = acquired.get();
    final AtomicBoolean done = new AtomicBoolean(false);

    final ServerCall<ReqT, RespT> wrappedCall =
        new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
          @Override
          public void close(final Status status, final Metadata trailers) {
            try {
              super.close(status, trailers);
            } finally {
              if (done.compareAndSet(false, true)) {
                if (status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                  limiterListener.onDropped();
                } else {
                  limiterListener.onSuccess();
                }
              }
            }
          }
        };

    return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
        next.startCall(wrappedCall, headers)) {
      @Override
      public void onCancel() {
        try {
          super.onCancel();
        } finally {
          if (done.compareAndSet(false, true)) {
            limiterListener.onDropped();
          }
        }
      }
    };
  }
}
