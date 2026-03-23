package com.agentengine.util.common.infra.events;

import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/**
 * A typed hot event channel: publishers may emit concurrently and each
 * subscriber receives only events published after that subscriber attaches to
 * the returned stream. Historical replay is intentionally out of scope.
 *
 * <p>Waiting for a specific event is a derived operation built on top of the
 * live event stream returned by {@link #events()}.
 */
public interface EventChannel<E> {

  void publish(E event);

  Publisher<E> events();

  CompletionStage<E> waitFor(Predicate<E> predicate, Duration timeout);

  /** Signal end-of-stream for the channel. Default is no-op. */
  default void complete() {
  }

  /** Signal an error for the channel. Default is no-op. */
  default void fail(final Throwable throwable) {
  }
}
