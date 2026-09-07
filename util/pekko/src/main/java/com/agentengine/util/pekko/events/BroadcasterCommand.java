package com.agentengine.util.pekko.events;

import com.agentengine.util.pekko.PekkoSerializable;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.pekko.actor.typed.ActorRef;

public interface BroadcasterCommand extends PekkoSerializable {
  record PublishCommand<Event>(
      @JsonTypeInfo(
              use = JsonTypeInfo.Id.CLASS,
              include = JsonTypeInfo.As.PROPERTY,
              property = "@class")
          Event payload,
      ActorRef<PublishAck> replyTo)
      implements BroadcasterCommand {}

  record SubscribeCommand(
      String subscriptionId,
      Long lastSeenSequence,
      ActorRef<SubscriberCommand> subscriber,
      ActorRef<SubscribeAck> replyTo)
      implements BroadcasterCommand {}

  record UnsubscribeCommand(String subscriptionId) implements BroadcasterCommand {}
}
