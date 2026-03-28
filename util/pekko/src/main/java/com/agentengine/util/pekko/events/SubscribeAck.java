package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.PekkoSerializable;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.List;

public record SubscribeAck(ActorRef<BroadcasterCommand> broadcasterRef, boolean replayAccepted, long oldestAvailableSequence,
    long latestAvailableSequence, List<SequencedEvent<?>> backlog) implements PekkoSerializable {
}