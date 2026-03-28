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
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.SpawnProtocol;
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
 * Distributed, scope-keyed event channel backed by a persistent sharded
 * broadcaster.
 */
public class PekkoEventChannel<Scope, Event>
    implements
      EventChannel<Scope, Event>,
      ShardedEntity.ShardedEntityDefinition<BroadcasterCommand, ShardingEnvelope<BroadcasterCommand>> {

  private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
  private static final int SUBSCRIBER_BUFFER_SIZE = 256;

  private final ActorSystem<SpawnProtocol.Command> system;
  private final ClusterSharding sharding;
  private final EntityTypeKey<BroadcasterCommand> typeKey;
  private final Entity<BroadcasterCommand, ShardingEnvelope<BroadcasterCommand>> entity;

  public PekkoEventChannel(final ActorSystem<SpawnProtocol.Command> system, final String channelName) {
    this.system = system;
    this.sharding = ClusterSharding.get(system);
    this.typeKey = EntityTypeKey.create(BroadcasterCommand.class, sanitizeTypeKey(channelName));
    this.entity = Entity
        .of(typeKey,
            entityContext -> Behaviors.setup(actorCtx -> new BroadcasterEntity(actorCtx, typeKey.name(), entityContext.getEntityId())))
        .withAllocationStrategy(RequesterFirstAllocationStrategy.INSTANCE);
  }

  @Override
  @SuppressWarnings("unchecked")
  public CompletionStage<EventSubscription<SequencedEvent<Event>>> subscribe(final Scope scope) {
    final String subscriptionId = UUID.randomUUID().toString();
    final FlowableProcessor<SequencedEvent<?>> processor = PublishProcessor.<SequencedEvent<?>>create().toSerialized();

    final Behavior<SubscriberCommand> subscriberBehavior = SubscriberActor.create(subscriptionId, broadcaster(scope), processor);
    final ActorRef<SubscriberCommand> subscriberActor = system.systemActorOf(subscriberBehavior, "event-subscriber-" + subscriptionId,
        Props.empty());

    final Publisher<SequencedEvent<Event>> publisher = processor.onBackpressureBuffer(SUBSCRIBER_BUFFER_SIZE, () -> {
    }, BackpressureOverflowStrategy.ERROR).map(event -> new SequencedEvent<>(event.sequence(), (Event) event.payload()))
        .doFinally(() -> stopSubscriber(subscriberActor).exceptionally(ignored -> null));

    return CompletionUtils.completeWithRootCause(AskPattern
        .ask(subscriberActor, (Function<ActorRef<SubscriberCommandResult>, SubscriberCommand>) SubscriberCommand.SubscribeCommand::new,
            COMMAND_TIMEOUT, system.scheduler())
        .thenCompose(result -> {
          if (result instanceof SubscriberCommandResult.Failed(Throwable cause)) {
            if (!processor.hasComplete() && !processor.hasThrowable()) {
              processor.onError(cause);
            }
            return CompletionUtils.failedStage(cause);
          }
          return CompletableFuture
              .completedFuture(new EventSubscription<>(subscriptionId, publisher, () -> stopSubscriber(subscriberActor)));
        }));
  }

  @Override
  public CompletionStage<Long> publish(final Scope scope, final Event event) {
    return CompletionUtils.completeWithRootCause(broadcaster(scope)
        .ask((Function<ActorRef<PublishAck>, BroadcasterCommand>) replyTo -> new BroadcasterCommand.PublishCommand<>(event, replyTo),
            COMMAND_TIMEOUT)
        .thenApply(PublishAck::sequence));
  }

  @Override
  public Entity<BroadcasterCommand, ShardingEnvelope<BroadcasterCommand>> entity() {
    return entity;
  }

  private CompletionStage<Void> stopSubscriber(final ActorRef<SubscriberCommand> subscriberActor) {
    return AskPattern
        .ask(subscriberActor, (Function<ActorRef<SubscriberCommandResult>, SubscriberCommand>) SubscriberCommand.UnsubscribeCommand::new,
            COMMAND_TIMEOUT, system.scheduler())
        .thenApply(ignored -> null);
  }

  private EntityRef<BroadcasterCommand> broadcaster(final Scope scope) {
    return sharding.entityRefFor(typeKey, scope.toString());
  }

  private static String sanitizeTypeKey(final String name) {
    return "event-channel-" + name.replaceAll("[^A-Za-z0-9_-]", "-");
  }
}
