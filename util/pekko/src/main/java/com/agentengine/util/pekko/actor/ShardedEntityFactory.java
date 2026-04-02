package com.agentengine.util.pekko.actor;

import com.agentengine.util.pekko.ActorSystemProvider;
import java.time.Duration;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingSettings;
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.japi.function.Function;

/**
 * Base factory for cluster-sharded entities. Handles sharding registration and entity ref
 * acquisition. The {@code builder} function receives the {@link EntityContext} and should return a
 * {@link Behavior} — typically a {@code Behaviors.setup()} wrapper so that the entity can receive
 * an {@code ActorContext} in its constructor.
 *
 * <p>An optional {@code role} constrains the shard coordinator singleton and entity hosting to
 * cluster members that carry that role, preventing the coordinator from migrating to nodes that
 * cannot actually run the entity.
 *
 * @param <Command> the entity's command type
 */
public abstract class ShardedEntityFactory<Command> implements ShardedEntityDefinition {

    private final ActorSystemProvider actorSystemProvider;
    private final EntityTypeKey<Command> entityTypeKey;
    private final Function<EntityContext<Command>, Behavior<Command>> builder;
    private final Duration passivationDuration;
    private final String role;

    protected ShardedEntityFactory(
            final ActorSystemProvider actorSystemProvider,
            final EntityTypeKey<Command> entityTypeKey,
            final Function<EntityContext<Command>, Behavior<Command>> builder,
            final Duration passivationDuration,
            final String role) {
        this.actorSystemProvider = actorSystemProvider;
        this.entityTypeKey = entityTypeKey;
        this.builder = builder;
        this.passivationDuration = passivationDuration;
        this.role = role;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M, E> Entity<M, E> entity(final ActorSystem<?> system) {
        Entity<Command, ShardingEnvelope<Command>> entity = Entity.of(entityTypeKey, builder);
        if (role != null) {
            entity = entity.withRole(role);
        }
        if (passivationDuration == null) {
            return (Entity<M, E>) entity;
        }
        final ClusterShardingSettings settings = ClusterShardingSettings.create(system)
                .withPassivationStrategy(ClusterShardingSettings.PassivationStrategySettings$.MODULE$
                        .defaults()
                        .withIdleEntityPassivation(passivationDuration));
        return (Entity<M, E>) entity.withSettings(settings);
    }

    public EntityRef<Command> entityRef(final String id) {
        return actorSystemProvider.entityRefFor(entityTypeKey, id);
    }
}
