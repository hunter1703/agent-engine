package com.agentengine.util.pekko.events;

import com.agentengine.util.common.CompletionUtils;
import com.agentengine.util.common.infra.events.EventChannel;
import com.agentengine.util.pekko.actor.BroadcastBehavior;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/**
 * {@link EventChannel} backed by a Pekko {@link BroadcastBehavior} actor. Each
 * call to {@link #events()} creates a new subscriber actor that receives all
 * events published after subscription.
 */
public final class PekkoEventChannel<E> implements EventChannel<E> {
  private static final String GLOBAL_ENTITY_ID = "__global__";
  private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(5);

  private final ActorSystem<?> system;
  private final ClusterSharding sharding;
  private final EntityTypeKey<BroadcastBehavior.Command> typeKey;
  private final String entityId;
  private final Materializer materializer;

  public PekkoEventChannel(final ActorSystem<?> system) {
    this(system, "event-channel-" + UUID.randomUUID());
  }

  public PekkoEventChannel(final ActorSystem<?> system, final String name) {
    this(system, name, GLOBAL_ENTITY_ID);
  }

  public PekkoEventChannel(final ActorSystem<?> system, final String name, final String entityId) {
    this.system = system;
    this.sharding = ClusterSharding.get(system);
    this.typeKey = EntityTypeKey.create(BroadcastBehavior.Command.class, sanitizeTypeKey(name));
    this.sharding.init(Entity.of(typeKey, ctx -> BroadcastBehavior.create()));
    this.entityId = entityId;
    this.materializer = Materializer.createMaterializer(system);
  }

  @Override
  public void publish(final E event) {
    entityRef().tell(new BroadcastBehavior.Command.Publish(event));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Publisher<E> events() {
    final CompletableFuture<ActorRef<E>> subscriberRef = new CompletableFuture<>();
    final Publisher<E> publisher = ActorSource.<E>actorRef(msg -> false, msg -> Optional.empty(), 256, OverflowStrategy.dropHead())
        .mapMaterializedValue(ref -> {
          subscriberRef.complete(ref);
          return ref;
        })
        .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), materializer);

    final ActorRef<E> ref = subscriberRef.join();
    AskPattern.ask(entityRef(), (ActorRef<Done> replyTo) -> new BroadcastBehavior.Command.Subscribe((ActorRef<Object>) ref, replyTo),
        SUBSCRIPTION_TIMEOUT, system.scheduler()).toCompletableFuture().join();
    return publisher;
  }

  @Override
  public CompletionStage<E> waitFor(final Predicate<E> predicate, final Duration timeout) {
    return CompletionUtils.completeWithRootCause(
        Source.fromPublisher(events()).filter(predicate::test).take(1).completionTimeout(timeout).runWith(Sink.head(), materializer));
  }

  /**
   * Stops the underlying broadcast entity. When it stops, all subscriber
   * {@code ActorSource} streams fail with
   * {@code WatchedActorTerminatedException} — a deliberate signal that the
   * channel is gone rather than a clean completion.
   */
  public void stop() {
    entityRef().tell(new BroadcastBehavior.Command.Stop());
  }

  private EntityRef<BroadcastBehavior.Command> entityRef() {
    return sharding.entityRefFor(typeKey, entityId);
  }

  private static String sanitizeTypeKey(final String name) {
    return "event-channel-" + name.replaceAll("[^A-Za-z0-9_-]", "-");
  }
}
