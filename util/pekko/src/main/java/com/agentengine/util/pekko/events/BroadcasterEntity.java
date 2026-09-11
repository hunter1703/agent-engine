package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.Copyable;
import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.actor.ShardedEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.Address;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persistent sharded broadcaster with bounded replay for gap healing. */
public final class BroadcasterEntity
    extends ShardedEntity<BroadcasterCommand, BroadcasterFact, BroadcasterState> {

  private static final Logger LOG = LoggerFactory.getLogger(BroadcasterEntity.class);
  private static final int MAX_RETAINED_EVENTS = 256;

  private final ActorContext<BroadcasterCommand> context;
  private final Address selfAddress;
  // Split by locality once at subscribe time rather than re-checking on every publish: a local
  // subscriber shares this JVM with the broadcaster, so a plain tell() hands it the exact same
  // payload instance every other local subscriber gets. A remote subscriber's tell() always crosses
  // Pekko's remote serialization
  // boundary, which already produces an independent instance on the far end, so copying it again
  // here would be pure waste.
  private final Map<String, ActorRef<SubscriberCommand>> localSubscribers = new LinkedHashMap<>();
  private final Map<String, ActorRef<SubscriberCommand>> remoteSubscribers = new LinkedHashMap<>();

  public BroadcasterEntity(
      final ActorContext<BroadcasterCommand> context,
      final String typeKeyName,
      final String entityId) {
    super(typeKeyName, entityId);
    this.context = context;
    this.selfAddress = Cluster.get(context.getSystem()).selfMember().address();
  }

  @Override
  public BroadcasterState emptyState() {
    return BroadcasterState.empty();
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    return RetentionCriteria.snapshotEvery(MAX_RETAINED_EVENTS, 1).withDeleteEventsOnSnapshot();
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

    // The backlog reply crosses the same local/remote boundary as a live DeliverCommand -- a
    // locally-delivered backlog hands out this entity's own retained event instances, which must
    // never be mutated by whoever receives them.
    final List<SequencedEvent<Copyable<?>>> backlog = state.eventsAfter(command.lastSeenSequence());
    final List<SequencedEvent<Copyable<?>>> backlogForReply =
        isLocal(command.replyTo())
            ? backlog.stream().map(BroadcasterEntity::copyOf).toList()
            : backlog;

    return Effect()
        .none()
        .thenRun(
            ignored -> {
              attachSubscriber(command.subscriptionId(), command.subscriber());
              command
                  .replyTo()
                  .tell(
                      new SubscribeAck(
                          context.getSelf(),
                          replayAccepted,
                          state.oldestRetainedSequence(),
                          state.latestPublishedSequence(),
                          backlogForReply));
            });
  }

  private Effect<BroadcasterFact, BroadcasterState> publish(
      final BroadcasterState state, final BroadcasterCommand.PublishCommand command) {

    final long nextSeq = state.latestPublishedSequence() + 1;
    final SequencedEvent<Copyable<?>> event = new SequencedEvent<>(nextSeq, command.payload());
    LOG.debug(
        "publish sequence={} localSubscribers={} remoteSubscribers={}",
        nextSeq,
        localSubscribers.size(),
        remoteSubscribers.size());
    return Effect()
        .persist(new BroadcasterFact.PublishedFact(event))
        .thenRun(
            ignored -> {
              localSubscribers.forEach(
                  (_, subscriber) ->
                      subscriber.tell(new SubscriberCommand.DeliverCommand(copyOf(event))));
              remoteSubscribers.forEach(
                  (_, subscriber) -> subscriber.tell(new SubscriberCommand.DeliverCommand(event)));
            })
        .thenReply(command.replyTo(), ignored -> new PublishAck(event.sequence()));
  }

  private Effect<BroadcasterFact, BroadcasterState> unsubscribe(
      final BroadcasterState state, final BroadcasterCommand.UnsubscribeCommand command) {
    detachSubscriber(command.subscriptionId());
    return Effect().none();
  }

  private void attachSubscriber(
      final String subscriptionId, final ActorRef<SubscriberCommand> subscriber) {
    final boolean local = isLocal(subscriber);
    final Map<String, ActorRef<SubscriberCommand>> bucket =
        local ? localSubscribers : remoteSubscribers;
    final Map<String, ActorRef<SubscriberCommand>> otherBucket =
        local ? remoteSubscribers : localSubscribers;
    final ActorRef<SubscriberCommand> previous = bucket.put(subscriptionId, subscriber);
    final ActorRef<SubscriberCommand> previousElsewhere = otherBucket.remove(subscriptionId);
    final ActorRef<SubscriberCommand> replaced = previous != null ? previous : previousElsewhere;
    if (replaced != null && !replaced.equals(subscriber)) {
      context.unwatch(replaced);
    }
    context.watchWith(subscriber, new BroadcasterCommand.UnsubscribeCommand(subscriptionId));
  }

  private void detachSubscriber(final String subscriptionId) {
    final ActorRef<SubscriberCommand> removedLocal = localSubscribers.remove(subscriptionId);
    final ActorRef<SubscriberCommand> subscriber =
        removedLocal != null ? removedLocal : remoteSubscribers.remove(subscriptionId);
    if (subscriber != null) {
      context.unwatch(subscriber);
    }
  }

  private boolean isLocal(final ActorRef<?> ref) {
    final Address address = ref.path().address();
    return address.hasLocalScope() || address.equals(selfAddress);
  }

  private static SequencedEvent<Copyable<?>> copyOf(final SequencedEvent<Copyable<?>> event) {
    final Copyable<?> copy = event.payload().copy();
    return new SequencedEvent<>(event.sequence(), copy);
  }
}
