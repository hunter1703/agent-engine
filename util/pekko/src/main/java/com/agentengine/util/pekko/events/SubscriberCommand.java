package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.PekkoSerializable;
import org.apache.pekko.actor.DeadLetterSuppression;

public interface SubscriberCommand extends PekkoSerializable {
  /**
   * Sent by external callers (e.g. RxJava cancellable) that want the actor to stop. The actor
   * handles all cleanup — broadcaster unsubscription and emitter completion — in onPostStop().
   * Landing on a dead actor here just means the actor was already stopping on its own;
   * DeadLetterSuppression keeps that expected, harmless race off the dead-letter log without
   * touching pekko.log-dead-letters, which still needs to catch genuine misdirected sends.
   */
  record StopCommand() implements SubscriberCommand, DeadLetterSuppression {}

  record DeliverCommand(SequencedEvent<?> event) implements SubscriberCommand {}

  record BroadcasterTerminatedCommand() implements SubscriberCommand {}

  record ResubscribeCommand() implements SubscriberCommand {}

  record SubscribeResultCommand(SubscribeAck ack, Throwable error) implements SubscriberCommand {}
}
