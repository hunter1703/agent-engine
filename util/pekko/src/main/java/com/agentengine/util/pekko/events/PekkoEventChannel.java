package com.agentengine.util.pekko.events;

import com.agentengine.util.common.CompletionUtils;
import com.agentengine.util.common.infra.events.EventChannel;
import com.agentengine.util.pekko.actor.BroadcastBehavior;
import com.agentengine.util.pekko.actor.ShardedEntity;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.AsPublisher;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.typed.javadsl.ActorSource;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

public class PekkoEventChannel<Scope, Event> implements EventChannel<Scope, Event>, ShardedEntity.ShardedEntityDefinition<BroadcastBehavior.Command, ShardingEnvelope<BroadcastBehavior.Command>> {
  private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(5);
  private final ClusterSharding sharding;
  private final EntityTypeKey<BroadcastBehavior.Command> typeKey;
  private final Materializer materializer;
  private final ConcurrentMap<Scope, SingleChannel<Event>> channels = new ConcurrentHashMap<>();

  public PekkoEventChannel(final ActorSystem<?> system, Materializer materializer) {
    this(system, materializer, "scoped-event-channel-" + java.util.UUID.randomUUID());
  }

  public PekkoEventChannel(final ActorSystem<?> system, final Materializer materializer, final String channelName) {
    this.sharding = ClusterSharding.get(system);
    this.materializer = materializer;
    this.typeKey = EntityTypeKey.create(BroadcastBehavior.Command.class, sanitizeTypeKey(channelName));
  }

  @Override
  public void publish(final Scope scope, final Event event) {
    channel(scope).publish(event);
  }

  @Override
  public Publisher<Event> events(final Scope scope) {
    return channel(scope).events();
  }

  @Override
  public CompletionStage<Event> waitFor(final Scope scope, final Predicate<Event> predicate, final Duration timeout) {
    return channel(scope).waitFor(predicate, timeout);
  }

  @Override
  public void complete(final Scope scope) {
    channels.remove(scope);
    entityRef(scope).tell(new BroadcastBehavior.Command.Stop());
  }

  @Override
  public Entity<BroadcastBehavior.Command, ShardingEnvelope<BroadcastBehavior.Command>> entity() {
    return Entity.of(typeKey, ctx -> BroadcastBehavior.create());
  }

  private SingleChannel<Event> channel(final Scope scope) {
    return channels.computeIfAbsent(scope, k -> new SingleChannel<>(entityRef(scope), materializer));
  }

  private EntityRef<BroadcastBehavior.Command> entityRef(final Scope scope) {
    return sharding.entityRefFor(typeKey, scope.toString());
  }

  private static String sanitizeTypeKey(final String name) {
    return "event-channel-" + name.replaceAll("[^A-Za-z0-9_-]", "-");
  }

  private static class SingleChannel<E> {
    private final EntityRef<BroadcastBehavior.Command> broadcaster;
    private final Materializer materializer;

    private SingleChannel(final EntityRef<BroadcastBehavior.Command> broadcaster, final Materializer materializer) {
      this.broadcaster = broadcaster;
      this.materializer = materializer;
    }

    private void publish(final E event) {
      broadcaster.tell(new BroadcastBehavior.Command.Publish(event));
    }

    private Publisher<E> events() {
      final CompletableFuture<ActorRef<E>> subscriberRef = new CompletableFuture<>();
      final Publisher<E> publisher = ActorSource.<E>actorRef(msg -> false, msg -> Optional.empty(), 256, OverflowStrategy.dropHead())
          .mapMaterializedValue(ref -> {
            subscriberRef.complete(ref);
            return ref;
          })
          .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), materializer);

      final ActorRef<E> ref = subscriberRef.join();
      //noinspection unchecked
      broadcaster.ask((ActorRef<Done> replyTo) -> new BroadcastBehavior.Command.Subscribe((ActorRef<Object>) ref, replyTo), SUBSCRIPTION_TIMEOUT).toCompletableFuture().join();
      return publisher;
    }

    private CompletionStage<E> waitFor(final Predicate<E> predicate, final Duration timeout) {
      return CompletionUtils.completeWithRootCause(
          Source.fromPublisher(events()).filter(predicate::test).take(1).completionTimeout(timeout).runWith(Sink.head(), materializer));
    }
  }
}
