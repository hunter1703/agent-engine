package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.events.SubscriberCommand.BroadcasterTerminatedCommand;
import com.agentengine.util.pekko.events.SubscriberCommand.ResubscribeCommand;
import com.agentengine.util.pekko.events.SubscriberCommand.SubscribeCommand;
import com.agentengine.util.pekko.events.SubscriberCommand.SubscribeResultCommand;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side actor that bridges the broadcaster protocol to a reactive-stream subscriber.
 *
 * <p>This actor is intentionally local and ephemeral. It does not own durable subscription
 * progress; instead, it tracks the in-memory sequence position of the attached downstream processor
 * and re-subscribes to the broadcaster when it detects that it may have missed events.
 *
 * <p>Behavior summary:
 *
 * <ul>
 *   <li>Subscription becomes active only after the subscribe ack has been processed.
 *   <li>Until activation, direct live deliveries from the broadcaster are ignored.
 *   <li>On subscribe ack, the actor consumes the retained backlog supplied by the broadcaster and
 *       advances its local checkpoint as those events are accepted by the downstream processor.
 *   <li>After activation, delivery is contiguous: the next accepted event must have sequence {@code
 *       lastSeenSequence + 1}. Duplicate or stale events are ignored.
 *   <li>If a later event exposes a sequence gap, the actor re-subscribes and relies on the
 *       broadcaster's bounded retained backlog to heal the gap.
 *   <li>If the broadcaster terminates, the actor re-subscribes after a retry delay.
 * </ul>
 *
 * <p>Sequence semantics:
 *
 * <ul>
 *   <li>{@code lastSeenSequence} represents the highest contiguous sequence successfully handed to
 *       the downstream processor.
 *   <li>The checkpoint advances only after {@code processor.onNext(...)} returns successfully for
 *       that event.
 *   <li>This provides effectively-once style local progression for synchronous downstream
 *       consumers: duplicates are ignored, out-of-order forward jumps are rejected, and replay is
 *       requested on detected gaps.
 * </ul>
 *
 * <p>Bounded gap healing:
 *
 * <ul>
 *   <li>The broadcaster retains only a bounded replay window.
 *   <li>If the subscriber falls behind but the missing range is still retained, re-subscription
 *       heals the gap.
 *   <li>If the missing range has already fallen out of the retained window, subscription fails
 *       because the actor can no longer reconstruct a contiguous stream.
 * </ul>
 *
 * <p>Pre-activation buffering:
 *
 * <ul>
 *   <li>Live {@code DeliverCommand} messages that arrive before the subscribe ack is processed are
 *       buffered in memory and flushed after the backlog is applied. {@code applyEvent} deduplicates
 *       by sequence, so any overlap between the backlog and the buffer is harmless. This ensures
 *       events published during the subscription handshake window are never silently lost, including
 *       the case where a short run completes entirely within that window.
 * </ul>
 */
final class SubscriberActor extends AbstractBehavior<SubscriberCommand> {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriberActor.class);
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
    // Events received before activation are buffered and flushed after the subscribe ack so that
    // events published during the subscription handshake window are never lost.
    private final List<SequencedEvent<?>> preActivationBuffer = new ArrayList<>();

    private SubscriberActor(
            final ActorContext<SubscriberCommand> context,
            final String subscriptionId,
            final EntityRef<BroadcasterCommand> broadcasterEntity,
            final Subscriber<SequencedEvent<?>> processor) {
        super(context);
        this.subscriptionId = subscriptionId;
        this.broadcasterEntity = broadcasterEntity;
        this.processor = processor;
    }

    public static Behavior<SubscriberCommand> create(
            final String subscriptionId,
            final EntityRef<BroadcasterCommand> broadcasterEntity,
            final Subscriber<SequencedEvent<?>> downstream) {
        return Behaviors.setup(context -> new SubscriberActor(context, subscriptionId, broadcasterEntity, downstream));
    }

    @Override
    public Receive<SubscriberCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(SubscribeCommand.class, this::subscribe)
                .onMessage(SubscribeResultCommand.class, this::subscribeResult)
                .onMessage(ResubscribeCommand.class, ignored -> {
                    requestSubscribe();
                    return this;
                })
                .onMessage(SubscriberCommand.UnsubscribeCommand.class, this::unsubscribe)
                .onMessage(SubscriberCommand.DeliverCommand.class, this::deliver)
                .onMessage(BroadcasterTerminatedCommand.class, this::onBroadcasterTerminated)
                .onSignal(PostStop.class, ignored -> onPostStop())
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

        getContext()
                .pipeToSelf(
                        broadcasterEntity.ask(
                                (Function<ActorRef<SubscribeAck>, BroadcasterCommand>)
                                        replyTo -> new BroadcasterCommand.SubscribeCommand(
                                                subscriptionId,
                                                lastSeenSequence,
                                                getContext().getSelf(),
                                                replyTo),
                                COMMAND_TIMEOUT),
                        SubscribeResultCommand::new);
    }

    private Behavior<SubscriberCommand> subscribeResult(final SubscribeResultCommand command) {
        subscribeInFlight = false;

        if (command.error() != null) {
            broadcaster = null;
            LOG.warn(
                    "Subscribe failed for id={}, retrying in {}ms: {}",
                    subscriptionId,
                    RETRY_DELAY.toMillis(),
                    command.error().getMessage());

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
        LOG.debug(
                "Subscribe ack id={} replayAccepted={} backlogSize={} lastSeen={}",
                subscriptionId,
                ack.replayAccepted(),
                ack.backlog().size(),
                lastSeenSequence);
        if (!ack.replayAccepted()) {
            terminalError = new IllegalStateException(
                    "Replay gap exceeded retained window for subscription '%s'. Expected from sequence %d, oldest available is %d, latest available is %d."
                            .formatted(
                                    subscriptionId,
                                    lastSeenSequence + 1L,
                                    ack.oldestAvailableSequence(),
                                    ack.latestAvailableSequence()));

            if (pendingSubscribeReplyTo != null) {
                pendingSubscribeReplyTo.tell(new SubscriberCommandResult.Failed(terminalError));
                pendingSubscribeReplyTo = null;
            }

            return Behaviors.stopped();
        }

        watchBroadcaster(ack.broadcasterRef());

        try {
            ack.backlog().forEach(this::applyEvent);
            // Flush events that arrived during the subscription handshake window. applyEvent
            // deduplicates by sequence so overlap with the backlog is safe.
            preActivationBuffer.forEach(this::applyEvent);
            preActivationBuffer.clear();
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
            // Buffer deliveries that arrive before the subscribe ack so they are not silently
            // lost. They are flushed in subscribeResult() after the backlog is applied.
            preActivationBuffer.add(command.event());
            return this;
        }

        final SequencedEvent<?> event = command.event();
        final long sequence = event.sequence();

        if (lastSeenSequence != null && sequence <= lastSeenSequence) {
            return this;
        }

        if (lastSeenSequence != null && sequence != lastSeenSequence + 1L) {
            LOG.warn(
                    "Gap detected for id={}: expected seq={} got seq={}, resubscribing",
                    subscriptionId,
                    lastSeenSequence + 1L,
                    sequence);
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
        LOG.warn("Broadcaster terminated for id={}, resubscribing in {}ms", subscriptionId, RETRY_DELAY.toMillis());
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
            pendingSubscribeReplyTo.tell(new SubscriberCommandResult.Failed(
                    new IllegalStateException("Subscriber stopped before subscription completed")));
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
                throw new IllegalStateException(
                        "Non-contiguous sequence. Expected " + (lastSeenSequence + 1L) + " but got " + sequence);
            }
        }

        processor.onNext(event);
        lastSeenSequence = sequence;
    }
}
