package com.agentengine.util.pekko.events;

import com.agentengine.util.pekko.PekkoSerializable;

import java.util.Set;

public record BroadcasterState(long nextSequence, Set<String> subscriptions) implements PekkoSerializable {

    public static BroadcasterState empty() {
        return new BroadcasterState(1L, Set.of());
    }

    public boolean hasSubscription(final String subscriptionId) {
        return subscriptions.contains(subscriptionId);
    }

    public BroadcasterState withSubscription(final String subscriptionId) {
        subscriptions.add(subscriptionId);
        return this;
    }

    public BroadcasterState withoutSubscription(final String subscriptionId) {
        subscriptions.remove(subscriptionId);
        return this;
    }

    public BroadcasterState withSequence(final long sequence) {
        return new BroadcasterState(Math.max(nextSequence, sequence + 1L), subscriptions);
    }
}
