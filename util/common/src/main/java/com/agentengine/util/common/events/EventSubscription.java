package com.agentengine.util.common.events;

import org.reactivestreams.Publisher;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Handle to an active subscription with an explicit cancellable lifecycle.
 */
public final class EventSubscription<E> {
  private final String subscriptionId;
  private final Publisher<E> publisher;
  private final Supplier<CompletionStage<Void>> cancelAction;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final CompletableFuture<Void> cancelResult = new CompletableFuture<>();

  public EventSubscription(final String subscriptionId, final Publisher<E> publisher, final Supplier<CompletionStage<Void>> cancelAction) {
    this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
  }

  public String subscriptionId() {
    return subscriptionId;
  }

  public Publisher<E> publisher() {
    return publisher;
  }

  public CompletionStage<Void> cancel() {
    if (cancelled.compareAndSet(false, true)) {
      cancelAction.get().whenComplete((ignored, error) -> {
        if (error == null) {
          cancelResult.complete(null);
        } else {
          cancelResult.completeExceptionally(error);
        }
      });
    }
    return cancelResult;
  }
}
