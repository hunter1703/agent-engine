package com.agentengine.util.pekko.events;

import com.agentengine.util.pekko.PekkoSerializable;

public interface BroadcasterFact extends PekkoSerializable {
    record SubscriptionAdded(String subscriptionId) implements BroadcasterFact {
    }

    record SubscriptionRemoved(String subscriptionId) implements BroadcasterFact {
    }

    record SequenceAdvanced(long sequence) implements BroadcasterFact {
    }
}
