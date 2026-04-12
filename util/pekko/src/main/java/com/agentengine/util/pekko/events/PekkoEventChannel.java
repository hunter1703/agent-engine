package com.agentengine.util.pekko.events;

import com.agentengine.util.common.CompletionUtils;
import com.agentengine.util.common.events.EventChannel;
import com.agentengine.util.common.events.EventSubscription;
import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.actor.RequesterFirstAllocationStrategy;
import com.agentengine.util.pekko.actor.ShardedEntityDefinition;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
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

/** Distributed, scope-keyed event channel backed by a persistent sharded broadcaster. */
public class PekkoEventChannel<Scope, Event> implements EventChannel<Scope, Event>, ShardedEntityDefinition {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
    private static final int SUBSCRIBER_BUFFER_SIZE = 256;

    // ActorSystemProvider is stored rather than ActorSystem directly because ActorSystemProvider
    // initialises its system field in @Observes StartupEvent, which fires after CDI constructs
    // all singletons. Extracting system() eagerly in the constructor would capture null.
    private final ActorSystemProvider actorSystemProvider;
    private final EntityTypeKey<BroadcasterCommand> typeKey;
    private final Entity<BroadcasterCommand, ShardingEnvelope<BroadcasterCommand>> entityDef;

    public PekkoEventChannel(final ActorSystemProvider actorSystemProvider, final String channelName) {
        this.actorSystemProvider = actorSystemProvider;
        this.typeKey = EntityTypeKey.create(BroadcasterCommand.class, sanitizeTypeKey(channelName));
        this.entityDef = Entity.of(
                        typeKey,
                        entityContext -> Behaviors.setup(actorCtx ->
                                new BroadcasterEntity(actorCtx, typeKey.name(), entityContext.getEntityId())))
                .withAllocationStrategy(RequesterFirstAllocationStrategy.INSTANCE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<EventSubscription<SequencedEvent<Event>>> subscribe(final Scope scope) {
        final String subscriptionId = UUID.randomUUID().toString();

        final Publisher<SequencedEvent<Event>> publisher = Flowable
                .<SequencedEvent<?>>create(emitter -> {
                    final ActorRef<SubscriberCommand> actor = AskPattern.ask(
                                    actorSystemProvider.system(),
                                    (Function<ActorRef<ActorRef<SubscriberCommand>>, SpawnProtocol.Command>)
                                            replyTo -> new SpawnProtocol.Spawn<>(
                                                    SubscriberActor.create(subscriptionId, broadcaster(scope), emitter),
                                                    "event-subscriber-" + subscriptionId,
                                                    Props.empty(),
                                                    replyTo),
                                    COMMAND_TIMEOUT,
                                    actorSystemProvider.system().scheduler())
                            .toCompletableFuture()
                            .join();
                    emitter.setCancellable(() -> actor.tell(new SubscriberCommand.UnsubscribeCommand(null)));
                }, BackpressureStrategy.MISSING)
                .onBackpressureBuffer(SUBSCRIBER_BUFFER_SIZE, () -> {}, BackpressureOverflowStrategy.ERROR)
                .map(event -> new SequencedEvent<>(event.sequence(), (Event) event.payload()));

        return CompletableFuture.completedFuture(new EventSubscription<>(subscriptionId, publisher));
    }

    @Override
    public CompletionStage<Long> publish(final Scope scope, final Event event) {
        return CompletionUtils.completeWithRootCause(broadcaster(scope)
                .ask(
                        (Function<ActorRef<PublishAck>, BroadcasterCommand>)
                                replyTo -> new BroadcasterCommand.PublishCommand<>(event, replyTo),
                        COMMAND_TIMEOUT)
                .thenApply(PublishAck::sequence));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M, E> Entity<M, E> entity(final ActorSystem<?> system) {
        return (Entity<M, E>) entityDef;
    }

    private EntityRef<BroadcasterCommand> broadcaster(final Scope scope) {
        return ClusterSharding.get(actorSystemProvider.system()).entityRefFor(typeKey, scope.toString());
    }

    private static String sanitizeTypeKey(final String name) {
        return "event-channel-" + name.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
