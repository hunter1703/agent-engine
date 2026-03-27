package com.agentengine.util.pekko.events;

import com.agentengine.util.pekko.PekkoSerializable;
import org.apache.pekko.actor.typed.ActorRef;

public interface Command extends PekkoSerializable {
    record Start(ActorRef<CommandResult> replyTo) implements Command {
    }

    record Stop(ActorRef<CommandResult> replyTo) implements Command {
    }

    record Deliver(long sequence, Object payload) implements Command {
    }

    record SubscribeResult(PekkoEventChannel.SubscribeAck ack, Throwable error) implements Command {
    }

    record BroadcasterTerminated() implements Command {
    }
}
