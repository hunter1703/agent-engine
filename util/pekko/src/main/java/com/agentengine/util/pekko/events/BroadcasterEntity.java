package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.actor.ShardedEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persistent sharded broadcaster with bounded replay for gap healing. */
public final class BroadcasterEntity extends ShardedEntity<BroadcasterCommand, BroadcasterFact, BroadcasterState> {

    private static final Logger LOG = LoggerFactory.getLogger(BroadcasterEntity.class);
    private static final int MAX_RETAINED_EVENTS = 256;

    private final ActorContext<BroadcasterCommand> context;
    private final Map<String, ActorRef<SubscriberCommand>> subscribers = new LinkedHashMap<>();

    public BroadcasterEntity(
            final ActorContext<BroadcasterCommand> context, final String typeKeyName, final String entityId) {
        super(typeKeyName, entityId);
        this.context = context;
    }

    @Override
    public BroadcasterState emptyState() {
        return BroadcasterState.empty();
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(MAX_RETAINED_EVENTS, 1);
    }

    @Override
    public CommandHandler<BroadcasterCommand, BroadcasterFact, BroadcasterState> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(BroadcasterCommand.SubscribeCommand.class, this::subscribe)
                .onCommand(BroadcasterCommand.PublishCommand.class, this::publish)
                .onCommand(BroadcasterCommand.UnsubscribeCommand.class, this::unsubscribe)
                .build();
    }

    @Override
    public EventHandler<BroadcasterState, BroadcasterFact> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(
                        BroadcasterFact.PublishedFact.class,
                        (state, fact) -> state.withPublished(fact.event(), MAX_RETAINED_EVENTS))
                .build();
    }

    private Effect<BroadcasterFact, BroadcasterState> subscribe(
            final BroadcasterState state, final BroadcasterCommand.SubscribeCommand command) {

        final boolean replayAccepted = state.canReplayFrom(command.lastSeenSequence());
        LOG.debug(
                "subscribe id={} lastSeen={} replayAccepted={} oldest={} latest={}",
                command.subscriptionId(),
                command.lastSeenSequence(),
                replayAccepted,
                state.oldestRetainedSequence(),
                state.latestPublishedSequence());

        return Effect().none().thenRun(ignored -> {
            if (replayAccepted) {
                attachSubscriber(command.subscriptionId(), command.subscriber());
            }
            command.replyTo()
                    .tell(new SubscribeAck(
                            context.getSelf(),
                            replayAccepted,
                            state.oldestRetainedSequence(),
                            state.latestPublishedSequence(),
                            state.eventsAfter(command.lastSeenSequence())));
        });
    }

    private Effect<BroadcasterFact, BroadcasterState> publish(
            final BroadcasterState state, final BroadcasterCommand.PublishCommand<?> command) {

        final long nextSeq = state.latestPublishedSequence() + 1;
        final SequencedEvent<?> event = new SequencedEvent<>(nextSeq, command.payload());
        LOG.debug("publish seq={} subscribers={}", nextSeq, subscribers.size());
        return Effect()
                .persist(new BroadcasterFact.PublishedFact(event))
                .thenRun(ignored -> subscribers.forEach(
                        (_, subscriber) -> subscriber.tell(new SubscriberCommand.DeliverCommand(event))))
                .thenReply(command.replyTo(), ignored -> new PublishAck(event.sequence()));
    }

    private Effect<BroadcasterFact, BroadcasterState> unsubscribe(
            final BroadcasterState state, final BroadcasterCommand.UnsubscribeCommand command) {
        detachSubscriber(command.subscriptionId());
        return Effect().none();
    }

    private void attachSubscriber(final String subscriptionId, final ActorRef<SubscriberCommand> subscriber) {
        final ActorRef<SubscriberCommand> previous = subscribers.put(subscriptionId, subscriber);
        if (previous != null && !previous.equals(subscriber)) {
            context.unwatch(previous);
        }
        context.watchWith(subscriber, new BroadcasterCommand.UnsubscribeCommand(subscriptionId));
    }

    private void detachSubscriber(final String subscriptionId) {
        final ActorRef<SubscriberCommand> subscriber = subscribers.remove(subscriptionId);
        if (subscriber != null) {
            context.unwatch(subscriber);
        }
    }
}
