package com.agentengine.util.common.events;

import java.util.Objects;
import org.reactivestreams.Publisher;

/** Handle to an active subscription. Cleanup is managed by the underlying reactive stream. */
public record EventSubscription<E>(String subscriptionId, Publisher<E> publisher) {
  public EventSubscription(final String subscriptionId, final Publisher<E> publisher) {
    this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
  }
}
