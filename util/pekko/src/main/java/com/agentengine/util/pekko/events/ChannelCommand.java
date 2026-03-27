package com.agentengine.util.pekko.events;

import com.agentengine.util.pekko.PekkoSerializable;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelCommand extends PekkoSerializable {
    record Publish<Event>(Event payload, ActorRef<PublishAck> replyTo) implements ChannelCommand {
    }

    record Subscribe(String subscriptionId, ActorRef<Command> subscriber,
                     ActorRef<SubscribeAck> replyTo) implements ChannelCommand {
    }

    record StopSubscriber(String subscriptionId) implements ChannelCommand {
    }

    record PublishAck(long sequence) implements PekkoSerializable {
    }

    record SubscribeAck(ActorRef<ChannelCommand> broadcasterRef) implements PekkoSerializable {
    }
}
