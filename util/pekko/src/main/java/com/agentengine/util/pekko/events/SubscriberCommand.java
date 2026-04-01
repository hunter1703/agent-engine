package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.PekkoSerializable;
import org.apache.pekko.actor.typed.ActorRef;

public interface SubscriberCommand extends PekkoSerializable {
    record SubscribeCommand(ActorRef<SubscriberCommandResult> replyTo) implements SubscriberCommand {}

    record UnsubscribeCommand(ActorRef<SubscriberCommandResult> replyTo) implements SubscriberCommand {}

    record DeliverCommand(SequencedEvent<?> event) implements SubscriberCommand {}

    record BroadcasterTerminatedCommand() implements SubscriberCommand {}

    record ResubscribeCommand() implements SubscriberCommand {}

    record SubscribeResultCommand(SubscribeAck ack, Throwable error) implements SubscriberCommand {}
}
