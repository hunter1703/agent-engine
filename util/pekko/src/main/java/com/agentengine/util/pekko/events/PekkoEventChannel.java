package com.agentengine.util.pekko.events;

import com.agentengine.util.common.CompletionUtils;
import com.agentengine.util.common.events.EventChannel;
import com.agentengine.util.common.events.EventSubscription;
import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.actor.RequesterFirstAllocationStrategy;
import com.agentengine.util.pekko.actor.ShardedEntity;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.japi.function.Function;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Distributed, scope-keyed event channel backed by a persistent sharded broadcaster.
 * <p>
 * Delivery contract:
 * <ul>
 *   <li>{@code subscribe} linearizes when the returned stage completes — all events
 *       published after that point are delivered at least once to the subscriber's mailbox.</li>
 *   <li>{@code publish} linearizes when the returned stage completes and returns the
 *       assigned monotonic sequence.</li>
 *   <li>Cancellation is via {@link EventSubscription#cancel()}; the subscriber actor stops,
 *       broadcaster state is cleaned up via DeathWatch.</li>
 *   <li>If the broadcaster recovers from passivation, subscribers re-register automatically
 *       via DeathWatch. Events during the re-registration window are not guaranteed.</li>
 * </ul>
 */
public class PekkoEventChannel<Scope, Event> implements EventChannel<Scope, Event>,
    ShardedEntity.ShardedEntityDefinition<ChannelCommand, ShardingEnvelope<ChannelCommand>> {

  private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
  private static final int SUBSCRIBER_BUFFER_SIZE = 256;

  private final ActorSystem<SpawnProtocol.Command> system;
  private final ClusterSharding sharding;
  private final EntityTypeKey<ChannelCommand> typeKey;
  private final Entity<ChannelCommand, ShardingEnvelope<ChannelCommand>> entity;

  public PekkoEventChannel(
      final ActorSystem<SpawnProtocol.Command> system,
      final String channelName) {
    this.system = system;
    this.sharding = ClusterSharding.get(system);
    this.typeKey = EntityTypeKey.create(ChannelCommand.class, sanitizeTypeKey(channelName));
    this.entity = Entity.of(typeKey, ctx -> Behaviors.setup(actorCtx ->
            new EventBroadcasterEntity(actorCtx, typeKey.name(), ctx.getEntityId())))
        .withAllocationStrategy(RequesterFirstAllocationStrategy.INSTANCE);
  }

  @Override
  @SuppressWarnings("unchecked")
  public CompletionStage<EventSubscription<SequencedEvent<Event>>> subscribe(final Scope scope) {
    final String subscriptionId = UUID.randomUUID().toString();
    final FlowableProcessor<SequencedEvent<Object>> processor = PublishProcessor.<SequencedEvent<Object>>create().toSerialized();

    final Behavior<Command> subscriberBehavior = SubscriberActor.create(subscriptionId, broadcaster(scope), processor);
    final ActorRef<Command> subscriberActor = system.systemActorOf(subscriberBehavior, "event-subscriber-" + subscriptionId, Props.empty());

    final Publisher<SequencedEvent<Event>> publisher = processor
        .onBackpressureBuffer(SUBSCRIBER_BUFFER_SIZE, () -> {}, BackpressureOverflowStrategy.ERROR)
        .map(event -> new SequencedEvent<>(event.sequence(), (Event) event.payload()))
        .doFinally(() -> stopSubscriber(subscriberActor).exceptionally(ignored -> null));

    return CompletionUtils.completeWithRootCause(
        AskPattern.ask(
                subscriberActor,
                (Function<ActorRef<CommandResult>, Command>)
                    Command.Start::new,
                COMMAND_TIMEOUT,
                system.scheduler())
            .thenCompose(result -> {
              if (result instanceof CommandResult.Failed failed) {
                if (!processor.hasComplete() && !processor.hasThrowable()) {
                  processor.onError(failed.cause());
                }
                return CompletionUtils.failedStage(failed.cause());
              }
              return CompletableFuture.completedFuture(new EventSubscription<>(
                  subscriptionId,
                  publisher,
                  () -> stopSubscriber(subscriberActor)));
            }));
  }

  @Override
  public CompletionStage<Long> publish(final Scope scope, final Event event) {
    return CompletionUtils.completeWithRootCause(
        broadcaster(scope)
            .ask((Function<ActorRef<ChannelCommand.PublishAck>, ChannelCommand>) replyTo ->
                new ChannelCommand.Publish<>(event, replyTo), COMMAND_TIMEOUT)
            .thenApply(ChannelCommand.PublishAck::sequence));
  }

  @Override
  public Entity<ChannelCommand, ShardingEnvelope<ChannelCommand>> entity() {
    return entity;
  }

  private CompletionStage<Void> stopSubscriber(final ActorRef<Command> subscriberActor) {
    return AskPattern.ask(
            subscriberActor,
            (Function<ActorRef<CommandResult>, Command>)
                Command.Stop::new,
            COMMAND_TIMEOUT,
            system.scheduler())
        .thenApply(ignored -> null);
  }

  private EntityRef<ChannelCommand> broadcaster(final Scope scope) {
    return sharding.entityRefFor(typeKey, scope.toString());
  }

  private static String sanitizeTypeKey(final String name) {
    return "event-channel-" + name.replaceAll("[^A-Za-z0-9_-]", "-");
  }

}
