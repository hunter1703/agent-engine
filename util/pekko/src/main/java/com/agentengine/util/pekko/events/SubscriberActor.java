package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.events.SubscriberCommand.BroadcasterTerminatedCommand;
import com.agentengine.util.pekko.events.SubscriberCommand.ResubscribeCommand;
import com.agentengine.util.pekko.events.SubscriberCommand.SubscribeCommand;
import com.agentengine.util.pekko.events.SubscriberCommand.SubscribeResultCommand;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.japi.function.Function;
import org.reactivestreams.Subscriber;

import java.time.Duration;

/**
 * Client-side actor that bridges the broadcaster protocol to a reactive-stream
 * subscriber.
 *
 * <p>
 * This actor is intentionally local and ephemeral. It does not own durable
 * subscription progress; instead, it tracks the in-memory sequence position of
 * the attached downstream processor and re-subscribes to the broadcaster when
 * it detects that it may have missed events.
 *
 * <p>
 * Behavior summary:
 * <ul>
 * <li>Subscription becomes active only after the subscribe ack has been
 * processed.</li>
 * <li>Until activation, direct live deliveries from the broadcaster are
 * ignored.</li>
 * <li>On subscribe ack, the actor consumes the retained backlog supplied by the
 * broadcaster and advances its local checkpoint as those events are accepted by
 * the downstream processor.</li>
 * <li>After activation, delivery is contiguous: the next accepted event must
 * have sequence {@code lastSeenSequence + 1}. Duplicate or stale events are
 * ignored.</li>
 * <li>If a later event exposes a sequence gap, the actor re-subscribes and
 * relies on the broadcaster's bounded retained backlog to heal the gap.</li>
 * <li>If the broadcaster terminates, the actor re-subscribes after a retry
 * delay.</li>
 * </ul>
 *
 * <p>
 * Sequence semantics:
 * <ul>
 * <li>{@code lastSeenSequence} represents the highest contiguous sequence
 * successfully handed to the downstream processor.</li>
 * <li>The checkpoint advances only after {@code processor.onNext(...)} returns
 * successfully for that event.</li>
 * <li>This provides effectively-once style local progression for synchronous
 * downstream consumers: duplicates are ignored, out-of-order forward jumps are
 * rejected, and replay is requested on detected gaps.</li>
 * </ul>
 *
 * <p>
 * Bounded gap healing:
 * <ul>
 * <li>The broadcaster retains only a bounded replay window.</li>
 * <li>If the subscriber falls behind but the missing range is still retained,
 * re-subscription heals the gap.</li>
 * <li>If the missing range has already fallen out of the retained window,
 * subscription fails because the actor can no longer reconstruct a contiguous
 * stream.</li>
 * </ul>
 *
 * <p>
 * Intentional limitation:
 * <ul>
 * <li>If a live event is ignored before activation and no later event arrives,
 * that missing tail event remains undetected until another publish occurs. In
 * other words, gap detection is triggered by observing a later sequence, not by
 * background polling.</li>
 * </ul>
 *
 * <p>
 * This tradeoff is intentional. The actor favors a simple and low-overhead
 * steady-state path, while still providing bounded replay-based recovery
 * instead of strict lossless delivery.
 */
final class SubscriberActor extends AbstractBehavior<SubscriberCommand> {

  private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(20);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

  private final String subscriptionId;
  private final EntityRef<BroadcasterCommand> broadcasterEntity;
  private final Subscriber<SequencedEvent<?>> processor;

  private ActorRef<BroadcasterCommand> broadcaster;
  private ActorRef<SubscriberCommandResult> pendingSubscribeReplyTo;
  private Throwable terminalError;
  private Long lastSeenSequence;
  private boolean subscribeInFlight;

  private SubscriberActor(final ActorContext<SubscriberCommand> context, final String subscriptionId,
      final EntityRef<BroadcasterCommand> broadcasterEntity, final Subscriber<SequencedEvent<?>> processor) {
    super(context);
    this.subscriptionId = subscriptionId;
    this.broadcasterEntity = broadcasterEntity;
    this.processor = processor;
  }

  public static Behavior<SubscriberCommand> create(final String subscriptionId, final EntityRef<BroadcasterCommand> broadcasterEntity,
      final Subscriber<SequencedEvent<?>> downstream) {
    return Behaviors.setup(context -> new SubscriberActor(context, subscriptionId, broadcasterEntity, downstream));
  }

  @Override
  public Receive<SubscriberCommand> createReceive() {
    return newReceiveBuilder().onMessage(SubscribeCommand.class, this::subscribe)
        .onMessage(SubscribeResultCommand.class, this::subscribeResult).onMessage(ResubscribeCommand.class, ignored -> {
          requestSubscribe();
          return this;
        }).onMessage(SubscriberCommand.UnsubscribeCommand.class, this::unsubscribe)
        .onMessage(SubscriberCommand.DeliverCommand.class, this::deliver)
        .onMessage(BroadcasterTerminatedCommand.class, this::onBroadcasterTerminated).onSignal(PostStop.class, ignored -> onPostStop())
        .build();
  }

  private Behavior<SubscriberCommand> subscribe(final SubscribeCommand command) {
    final ActorRef<SubscriberCommandResult> replyTo = command.replyTo();

    if (replyTo != null && isActive()) {
      replyTo.tell(new SubscriberCommandResult.Successful());
      return this;
    } else if (replyTo != null) {
      pendingSubscribeReplyTo = replyTo;
    }

    requestSubscribe();
    return this;
  }

  private void requestSubscribe() {
    if (subscribeInFlight) {
      return;
    }

    subscribeInFlight = true;

    getContext().pipeToSelf(broadcasterEntity
        .ask((Function<ActorRef<SubscribeAck>, BroadcasterCommand>) replyTo -> new BroadcasterCommand.SubscribeCommand(subscriptionId,
            lastSeenSequence, getContext().getSelf(), replyTo), COMMAND_TIMEOUT),
        SubscribeResultCommand::new);
  }

  private Behavior<SubscriberCommand> subscribeResult(final SubscribeResultCommand command) {
    subscribeInFlight = false;

    if (command.error() != null) {
      broadcaster = null;

      if (pendingSubscribeReplyTo != null) {
        terminalError = command.error();
        pendingSubscribeReplyTo.tell(new SubscriberCommandResult.Failed(terminalError));
        pendingSubscribeReplyTo = null;
        return Behaviors.stopped();
      }

      getContext().scheduleOnce(RETRY_DELAY, getContext().getSelf(), new ResubscribeCommand());
      return this;
    }

    final SubscribeAck ack = command.ack();
    if (!ack.replayAccepted()) {
      terminalError = new IllegalStateException(
          "Replay gap exceeded retained window for subscription '%s'. Expected from sequence %d, oldest available is %d, latest available is %d."
              .formatted(subscriptionId, lastSeenSequence + 1L, ack.oldestAvailableSequence(), ack.latestAvailableSequence()));

      if (pendingSubscribeReplyTo != null) {
        pendingSubscribeReplyTo.tell(new SubscriberCommandResult.Failed(terminalError));
        pendingSubscribeReplyTo = null;
      }

      return Behaviors.stopped();
    }

    watchBroadcaster(ack.broadcasterRef());

    try {
      ack.backlog().forEach(this::applyEvent);
    } catch (final Throwable error) {
      terminalError = error;
      if (pendingSubscribeReplyTo != null) {
        pendingSubscribeReplyTo.tell(new SubscriberCommandResult.Failed(error));
        pendingSubscribeReplyTo = null;
      }
      return Behaviors.stopped();
    }

    if (pendingSubscribeReplyTo != null) {
      pendingSubscribeReplyTo.tell(new SubscriberCommandResult.Successful());
      pendingSubscribeReplyTo = null;
    }

    if (lastSeenSequence == null) {
      lastSeenSequence = 0L;
    }
    return this;
  }

  private Behavior<SubscriberCommand> unsubscribe(final SubscriberCommand.UnsubscribeCommand command) {
    if (command.replyTo() != null) {
      command.replyTo().tell(new SubscriberCommandResult.Successful());
    }
    return Behaviors.stopped();
  }

  private Behavior<SubscriberCommand> deliver(final SubscriberCommand.DeliverCommand command) {
    if (!isActive()) {
      return this;
    }

    final SequencedEvent<?> event = command.event();
    final long sequence = event.sequence();

    if (lastSeenSequence != null && sequence <= lastSeenSequence) {
      return this;
    }

    if (lastSeenSequence != null && sequence != lastSeenSequence + 1L) {
      requestSubscribe();
      return this;
    }

    try {
      processor.onNext(event);
      lastSeenSequence = sequence;
      return this;
    } catch (final Throwable error) {
      terminalError = error;
      return Behaviors.stopped();
    }
  }

  private void watchBroadcaster(final ActorRef<BroadcasterCommand> newRef) {
    if (broadcaster != null && !broadcaster.equals(newRef)) {
      getContext().unwatch(broadcaster);
    }
    broadcaster = newRef;
    getContext().watchWith(broadcaster, new BroadcasterTerminatedCommand());
  }

  private Behavior<SubscriberCommand> onBroadcasterTerminated(final BroadcasterTerminatedCommand command) {
    broadcaster = null;
    getContext().scheduleOnce(RETRY_DELAY, getContext().getSelf(), new ResubscribeCommand());
    return this;
  }

  private boolean isActive() {
    return broadcaster != null;
  }

  private Behavior<SubscriberCommand> onPostStop() {
    if (broadcaster != null) {
      getContext().unwatch(broadcaster);
      broadcaster = null;
    }

    broadcasterEntity.tell(new BroadcasterCommand.UnsubscribeCommand(subscriptionId));

    if (pendingSubscribeReplyTo != null) {
      pendingSubscribeReplyTo
          .tell(new SubscriberCommandResult.Failed(new IllegalStateException("Subscriber stopped before subscription completed")));
      pendingSubscribeReplyTo = null;
    }

    if (terminalError != null) {
      processor.onError(terminalError);
    } else {
      processor.onComplete();
    }

    return this;
  }

  private void applyEvent(final SequencedEvent<?> event) {
    final long sequence = event.sequence();

    if (lastSeenSequence != null) {
      if (sequence <= lastSeenSequence) {
        return;
      }
      if (sequence != lastSeenSequence + 1L) {
        throw new IllegalStateException("Non-contiguous sequence. Expected " + (lastSeenSequence + 1L) + " but got " + sequence);
      }
    }

    processor.onNext(event);
    lastSeenSequence = sequence;
  }
}