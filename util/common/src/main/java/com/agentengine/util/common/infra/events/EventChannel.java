package com.agentengine.util.common.infra.events;

import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/**
 * A scoped hot event channel where each scope key {@code K} has its own
 * independent live stream. Publishers may emit concurrently and each
 * subscriber receives only events published after that subscriber attaches.
 */
public interface EventChannel<Scope, Event> {

  void publish(Scope scope, Event event);

  Publisher<Event> events(Scope scope);

  CompletionStage<Event> waitFor(Scope scope, Predicate<Event> predicate, Duration timeout);

  /** Signal end-of-stream for the given scope. */
  default void complete(Scope scope){}

  /** Signal an error for the given scope. */
  default void fail(Scope scope, Throwable throwable){}

  /** Signal end-of-stream for the channel. Default is no-op. */
  default void complete() {
  }

  /** Signal an error for the channel. Default is no-op. */
  default void fail(final Throwable throwable) {
  }
}
