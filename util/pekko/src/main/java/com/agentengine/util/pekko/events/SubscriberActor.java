package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.events.SubscriberCommand.BroadcasterTerminatedCommand;
import com.agentengine.util.pekko.events.SubscriberCommand.ResubscribeCommand;
import com.agentengine.util.pekko.events.SubscriberCommand.SubscribeResultCommand;
import io.reactivex.rxjava3.core.FlowableEmitter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side actor that bridges the broadcaster protocol to a reactive-stream subscriber.
 *
 * <p>This actor is intentionally local and ephemeral. It does not own durable subscription
 * progress; instead, it tracks the in-memory sequence position of the attached downstream emitter
 * and re-subscribes to the broadcaster when it detects that it may have missed events.
 *
 * <p>Behavior summary:
 *
 * <ul>
 *   <li>Subscription becomes active only after the subscribe ack has been processed.
 *   <li>Until activation, direct live deliveries from the broadcaster are buffered.
 *   <li>On subscribe ack, the actor consumes the retained backlog supplied by the broadcaster and
 *       advances its local checkpoint as those events are accepted by the downstream emitter.
 *   <li>After activation, delivery is contiguous: the next accepted event must have sequence {@code
 *       lastSeenSequence + 1}. Duplicate or stale events are ignored.
 *   <li>If a later event exposes a sequence gap, the actor re-subscribes and relies on the
 *       broadcaster's bounded retained backlog to heal the gap.
 *   <li>If the broadcaster terminates, the actor re-subscribes after a retry delay.
 * </ul>
 *
 * <p>New vs. re-subscription semantics:
 *
 * <ul>
 *   <li>A fresh subscription ({@code lastSeenSequence == null}) receives no backlog: the broadcaster
 *       returns {@code replayAccepted=false} and an empty backlog. The actor initialises its
 *       checkpoint to the broadcaster's current {@code latestAvailableSequence} so that only live
 *       events published after subscription are delivered.
 *   <li>A re-subscription ({@code lastSeenSequence != null}) requests a catch-up replay from the
 *       broadcaster. If the gap exceeds the broadcaster's bounded retention window, the subscription
 *       fails with an unrecoverable error.
 * </ul>
 *
 * <p>Pre-activation buffering:
 *
 * <ul>
 *   <li>The subscribe ack travels via {@code pipeToSelf} (an extra dispatch hop), while a
 *       concurrent {@code DeliverCommand} from the broadcaster is a direct tell. Live events can
 *       therefore arrive before the ack is processed. They are buffered and flushed after the
 *       backlog; {@code applyEvent} deduplicates by sequence so overlap is harmless.
 * </ul>
 */
final class SubscriberActor extends AbstractBehavior<SubscriberCommand> {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriberActor.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final String subscriptionId;
    private final EntityRef<BroadcasterCommand> broadcasterEntity;
    private final FlowableEmitter<SequencedEvent<?>> emitter;

    private ActorRef<BroadcasterCommand> broadcaster;
    private Throwable terminalError;
    private Long lastSeenSequence;
    private boolean subscribeInFlight;
    // DeliverCommands from a concurrent publication can arrive before SubscribeResultCommand because
    // the ack travels via pipeToSelf (an extra dispatch hop) while DeliverCommand is a direct tell.
    // Events are buffered here and flushed after the backlog is applied; applyEvent deduplicates.
    private final List<SequencedEvent<?>> preActivationBuffer = new ArrayList<>();

    private SubscriberActor(
            final ActorContext<SubscriberCommand> context,
            final String subscriptionId,
            final EntityRef<BroadcasterCommand> broadcasterEntity,
            final FlowableEmitter<SequencedEvent<?>> emitter) {
        super(context);
        this.subscriptionId = subscriptionId;
        this.broadcasterEntity = broadcasterEntity;
        this.emitter = emitter;
    }

    public static Behavior<SubscriberCommand> create(
            final String subscriptionId,
            final EntityRef<BroadcasterCommand> broadcasterEntity,
            final FlowableEmitter<SequencedEvent<?>> emitter) {
        return Behaviors.setup(ctx -> {
            final SubscriberActor actor =
                    new SubscriberActor(ctx, subscriptionId, broadcasterEntity, emitter.serialize());
            actor.requestSubscribe();
            return actor;
        });
    }

    @Override
    public Receive<SubscriberCommand> createReceive() {
        return newReceiveBuilder()
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

        // replayAccepted=false with a prior checkpoint means the gap exceeded the retention window.
        if (!ack.replayAccepted() && lastSeenSequence != null) {
            terminalError = new IllegalStateException(
                    "Replay gap exceeded retained window for subscription '%s'. Expected from sequence %d, oldest available is %d, latest available is %d."
                            .formatted(
                                    subscriptionId,
                                    lastSeenSequence + 1L,
                                    ack.oldestAvailableSequence(),
                                    ack.latestAvailableSequence()));
            return Behaviors.stopped();
        }

        watchBroadcaster(ack.broadcasterRef());

        // For a fresh subscription (no prior checkpoint), initialize to the broadcaster's current
        // position so that only live events published after subscription are delivered.
        if (lastSeenSequence == null) {
            lastSeenSequence = ack.latestAvailableSequence();
        }

        try {
            // backlog is empty for fresh subscriptions; non-empty for catch-up re-subscriptions.
            ack.backlog().forEach(this::applyEvent);
            // Flush events that arrived during the subscription handshake window. applyEvent
            // deduplicates by sequence so overlap with the backlog is safe.
            preActivationBuffer.forEach(this::applyEvent);
            preActivationBuffer.clear();
        } catch (final Throwable error) {
            terminalError = error;
            return Behaviors.stopped();
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

        if (sequence <= lastSeenSequence) {
            return this;
        }

        if (sequence != lastSeenSequence + 1L) {
            LOG.warn(
                    "Gap detected for id={}: expected seq={} got seq={}, resubscribing",
                    subscriptionId,
                    lastSeenSequence + 1L,
                    sequence);
            requestSubscribe();
            return this;
        }

        try {
            emitter.onNext(event);
            lastSeenSequence = sequence;
            if (emitter.isCancelled()) {
                return Behaviors.stopped();
            }
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

        if (terminalError != null) {
            emitter.onError(terminalError);
        } else {
            emitter.onComplete();
        }

        return this;
    }

    private void applyEvent(final SequencedEvent<?> event) {
        final long sequence = event.sequence();
        if (sequence <= lastSeenSequence) {
            return;
        }
        if (sequence != lastSeenSequence + 1L) {
            throw new IllegalStateException(
                    "Non-contiguous sequence. Expected " + (lastSeenSequence + 1L) + " but got " + sequence);
        }
        lastSeenSequence = sequence;
        if (emitter.isCancelled()) {
            throw new IllegalStateException("Downstream cancelled during event delivery");
        }
        emitter.onNext(event);
    }
}
