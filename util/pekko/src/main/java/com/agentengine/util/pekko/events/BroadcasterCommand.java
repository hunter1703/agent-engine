package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.Copyable;
import com.agentengine.util.pekko.PekkoSerializable;
import org.apache.pekko.actor.typed.ActorRef;

public sealed interface BroadcasterCommand extends PekkoSerializable {
  record PublishCommand(Copyable<?> payload, ActorRef<PublishAck> replyTo)
      implements BroadcasterCommand {}

  record SubscribeCommand(
      String subscriptionId,
      Long lastSeenSequence,
      ActorRef<SubscriberCommand> subscriber,
      ActorRef<SubscribeAck> replyTo)
      implements BroadcasterCommand {}

  record UnsubscribeCommand(String subscriptionId) implements BroadcasterCommand {}
}
