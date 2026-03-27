package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.PekkoSerializable;
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
 * Client-side actor bridging the broadcaster protocol to a reactive stream.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>{@code Start} → subscribes to broadcaster, replies when confirmed.</li>
 *   <li>{@code Deliver} → pushes events to the downstream processor.</li>
 *   <li>{@code Stop} → replies immediately and stops; PostStop completes the stream
 *       and notifies the broadcaster via fire-and-forget + DeathWatch.</li>
 *   <li>If the broadcaster terminates (passivation/crash), the actor re-subscribes
 *       automatically.</li>
 * </ol>
 */
final class PekkoSubscriberActor extends AbstractBehavior<PekkoSubscriberActor.Command> {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final String subscriptionId;
    private final EntityRef<PekkoEventChannel.ChannelCommand> broadcasterEntity;
    private final Subscriber<SequencedEvent<Object>> downstream;
    private Phase phase = Phase.INIT;
    private ActorRef<PekkoEventChannel.ChannelCommand> broadcaster;
    private ActorRef<CommandResult> startReplyTo;
    private Throwable terminalError;

    private PekkoSubscriberActor(
            final ActorContext<Command> context,
            final String subscriptionId,
            final EntityRef<PekkoEventChannel.ChannelCommand> broadcasterEntity,
            final Subscriber<SequencedEvent<Object>> downstream) {
        super(context);
        this.subscriptionId = subscriptionId;
        this.broadcasterEntity = broadcasterEntity;
        this.downstream = downstream;
    }

    public static Behavior<Command> create(
            final String subscriptionId,
            final EntityRef<PekkoEventChannel.ChannelCommand> broadcasterEntity,
            final Subscriber<SequencedEvent<Object>> downstream) {
        return Behaviors.setup(ctx -> new PekkoSubscriberActor(ctx, subscriptionId, broadcasterEntity, downstream));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Command.Start.class, this::onStart)
                .onMessage(Command.Stop.class, this::onStop)
                .onMessage(Command.Deliver.class, this::onDeliver)
                .onMessage(Command.SubscribeResult.class, this::onSubscribeResult)
                .onMessage(Command.BroadcasterTerminated.class, this::onBroadcasterTerminated)
                .onSignal(PostStop.class, ignored -> onPostStop())
                .build();
    }

    private Behavior<Command> onStart(final Command.Start command) {
        final ActorRef<CommandResult> replyTo = command.replyTo();
        if (phase == Phase.ACTIVE) {
            if (replyTo != null) {
                replyTo.tell(CommandResult.Completed.INSTANCE);
            }
            return this;
        }
        if (replyTo != null) {
            startReplyTo = replyTo;
        }
        requestSubscribe();
        return this;
    }

    private void requestSubscribe() {
        if (phase == Phase.INIT) {
            phase = Phase.SUBSCRIBING;
        } else if (phase == Phase.RECONNECTING) {
            phase = Phase.RESUBSCRIBING;
        } else {
            return;
        }
        getContext().pipeToSelf(
                broadcasterEntity.ask(
                        (Function<ActorRef<PekkoEventChannel.SubscribeAck>, PekkoEventChannel.ChannelCommand>) replyTo ->
                                new PekkoEventChannel.ChannelCommand.Subscribe(subscriptionId, getContext().getSelf(), replyTo),
                        COMMAND_TIMEOUT), Command.SubscribeResult::new);
    }

    private Behavior<Command> onStop(final Command.Stop command) {
        phase = Phase.STOPPING;
        command.replyTo().tell(CommandResult.Completed.INSTANCE);
        return Behaviors.stopped();
    }

    private Behavior<Command> onDeliver(final Command.Deliver command) {
        if (phase == Phase.ACTIVE) {
            downstream.onNext(new SequencedEvent<>(command.sequence(), command.payload()));
        }
        return this;
    }

    private Behavior<Command> onSubscribeResult(final Command.SubscribeResult command) {
        if (phase == Phase.STOPPING) {
            return this;
        }
        if (phase != Phase.SUBSCRIBING && phase != Phase.RESUBSCRIBING) {
            return this;
        }
        if (command.error() != null) {
            return handleSubscribeFailure(command.error());
        }
        watchBroadcaster(command.ack().broadcasterRef());
        phase = Phase.ACTIVE;
        if (startReplyTo != null) {
            startReplyTo.tell(CommandResult.Completed.INSTANCE);
            startReplyTo = null;
        }
        return this;
    }

    private void watchBroadcaster(final ActorRef<PekkoEventChannel.ChannelCommand> newRef) {
        if (broadcaster != null && !broadcaster.equals(newRef)) {
            getContext().unwatch(broadcaster);
        }
        broadcaster = newRef;
        getContext().watchWith(broadcaster, new Command.BroadcasterTerminated());
    }

    private Behavior<Command> handleSubscribeFailure(final Throwable error) {
        if (phase == Phase.SUBSCRIBING) {
            phase = Phase.STOPPING;
            terminalError = error;
            startReplyTo.tell(new CommandResult.Failed(error));
            startReplyTo = null;
            return Behaviors.stopped();
        }
        phase = Phase.RECONNECTING;
        getContext().scheduleOnce(RETRY_DELAY, getContext().getSelf(), new Command.Start(null));
        return this;
    }

    interface CommandResult extends PekkoSerializable {
        enum Completed implements CommandResult {INSTANCE}

        record Failed(Throwable cause) implements CommandResult {
        }
    }

    private Behavior<Command> onBroadcasterTerminated(final Command.BroadcasterTerminated command) {
        broadcaster = null;
        if (phase == Phase.STOPPING) {
            return this;
        }
        phase = Phase.RECONNECTING;
        requestSubscribe();
        return this;
    }

    private Behavior<Command> onPostStop() {
        if (terminalError != null) {
            downstream.onError(terminalError);
        } else {
            downstream.onComplete();
        }
        if (startReplyTo != null) {
            startReplyTo.tell(new CommandResult.Failed(
                    new IllegalStateException("Subscriber stopped before subscription confirmed")));
        }
        broadcasterEntity.tell(new PekkoEventChannel.ChannelCommand.SubscriberStopped(subscriptionId));
        return this;
    }

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

    private enum Phase {INIT, SUBSCRIBING, ACTIVE, RECONNECTING, RESUBSCRIBING, STOPPING}
}
