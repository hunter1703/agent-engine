package com.agentengine.interfaces.rest;

import com.agentengine.util.common.Defaults;
import com.agentengine.util.common.FlowableUtils;
import com.agentengine.util.common.JsonCodec;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Writes an SSE {@link Flowable} of individual items to a raw Vert.x {@link HttpServerResponse},
 * bypassing RESTEasy Reactive's built-in {@code Publisher<T>}-to-SSE bridge -- that bridge flushes
 * once per {@code onNext} with no batching hook of its own (confirmed by reading {@code
 * PublisherResponseHandler.SseMultiSubscriber}: each item's write must complete before the next
 * item is even requested), so it can't produce fewer writes than there are logical items no matter
 * how the upstream {@link Flowable} is shaped.
 *
 * <p>Re-batches with the same {@link Defaults} window/size {@code GRPCServerImpl} uses server-side
 * before writing -- items that arrived together in one gRPC batch are emitted back-to-back with no
 * delay between them, so the buffer fills and flushes immediately for them; a lone trickle item
 * waits at most the flush interval, the same latency tradeoff {@code GRPCServerImpl} already
 * accepts for its own batching. Keeping this here (not in the transport) keeps {@code
 * MicroServiceInvocationHandler}'s {@code Publisher<T>} an honest one-item-per-onNext contract for
 * every caller, typed or raw.
 */
public final class RestUtils {

  private static final byte[] DATA_PREFIX = "data:".getBytes(StandardCharsets.UTF_8);
  private static final byte[] EVENT_SUFFIX = "\n\n".getBytes(StandardCharsets.UTF_8);

  private RestUtils() {}

  public static Uni<Void> writeBatchedSSE(
      final HttpServerResponse response, final Flowable<?> events, final JsonCodec jsonCodec) {
    response.setChunked(true);
    response.putHeader("Content-Type", "text/event-stream");
    response.putHeader("Cache-Control", "no-cache");

    final CompletableFuture<Void> completion = new CompletableFuture<>();
    events
        .buffer(
            Defaults.STREAMING_BATCH_FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
            FlowableUtils.streamingScheduler(),
            Defaults.STREAMING_BATCH_SIZE)
        .filter(batch -> !batch.isEmpty())
        .map(batch -> encodeBatch(batch, jsonCodec))
        // concatMapCompletable preserves order and only requests the next batch once the current
        // write actually completes -- writes stay sequential, matching the backpressure the
        // built-in SSE bridge already gave us, just one write per batch instead of per element.
        .concatMapCompletable(
            bytes ->
                Completable.fromCompletionStage(
                    response.write(Buffer.buffer(bytes)).toCompletionStage()))
        .subscribe(
            () -> {
              response.end();
              completion.complete(null);
            },
            throwable -> {
              if (!response.ended()) {
                response.end();
              }
              completion.completeExceptionally(throwable);
            });
    return Uni.createFrom().completionStage(completion);
  }

  // Writes UTF-8 bytes directly instead of building a StringBuilder and converting it once at the
  // end -- a StringBuilder's own internal buffer is compact chars (Latin-1 or UTF-16), never UTF-8,
  // so toString()+getBytes() would still need a real encoding pass; this just skips the extra
  // intermediate String allocation/copy that toString() would otherwise do first.
  private static byte[] encodeBatch(final List<?> items, final JsonCodec jsonCodec) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (final Object item : items) {
      out.writeBytes(DATA_PREFIX);
      // "raw" MicroService calls (getRaw()) hand back already-serialized JSON as bytes (see
      // JsonCodec.splitJsonArray(byte[])) -- written straight through, no decode/re-encode.
      if (item instanceof byte[] rawJsonBytes) {
        out.writeBytes(rawJsonBytes);
      } else if (item instanceof String rawJson) {
        out.writeBytes(rawJson.getBytes(StandardCharsets.UTF_8));
      } else {
        out.writeBytes(jsonCodec.serialize(item).getBytes(StandardCharsets.UTF_8));
      }
      out.writeBytes(EVENT_SUFFIX);
    }
    return out.toByteArray();
  }
}
