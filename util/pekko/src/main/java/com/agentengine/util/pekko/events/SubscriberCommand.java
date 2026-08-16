package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.PekkoSerializable;

public interface SubscriberCommand extends PekkoSerializable {
  /**
   * Sent by external callers (e.g. RxJava cancellable) that want the actor to stop. The actor
   * handles all cleanup — broadcaster unsubscription and emitter completion — in onPostStop().
   */
  record StopCommand() implements SubscriberCommand {}

  record DeliverCommand(SequencedEvent<?> event) implements SubscriberCommand {}

  record BroadcasterTerminatedCommand() implements SubscriberCommand {}

  record ResubscribeCommand() implements SubscriberCommand {}

  record SubscribeResultCommand(SubscribeAck ack, Throwable error) implements SubscriberCommand {}
}
