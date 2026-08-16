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

/**
 * Base factory for cluster-sharded entities. Handles sharding registration and entity ref
 * acquisition; subclasses supply only {@link #behavior(EntityContext)}, typically a {@code
 * Behaviors.setup()} wrapper so the entity receives an {@code ActorContext} in its constructor.
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
    private final Duration passivationDuration;
    private final String role;

    protected ShardedEntityFactory(
            final ActorSystemProvider actorSystemProvider,
            final EntityTypeKey<Command> entityTypeKey,
            final Duration passivationDuration,
            final String role) {
        this.actorSystemProvider = actorSystemProvider;
        this.entityTypeKey = entityTypeKey;
        this.passivationDuration = passivationDuration;
        this.role = role;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M, E> Entity<M, E> entity(final ActorSystem<?> system) {
        ClusterShardingSettings settings =
                ClusterShardingSettings.create(system).withRememberEntities(rememberEntities());
        if (passivationDuration != null) {
            settings = settings.withPassivationStrategy(ClusterShardingSettings.PassivationStrategySettings$.MODULE$
                    .defaults()
                    .withIdleEntityPassivation(passivationDuration));
        }
        Entity<Command, ShardingEnvelope<Command>> entity =
                Entity.of(entityTypeKey, this::behavior).withSettings(settings);
        if (role != null) {
            entity = entity.withRole(role);
        }
        return (Entity<M, E>) entity;
    }

    /**
     * Builds the behaviour for one entity. A method rather than a constructor argument so that a
     * subclass can use its own fields — anything passed to {@code super(...)} has to exist before the
     * subclass's fields are assigned, which forces its collaborators to be built somewhere else.
     */
    protected abstract Behavior<Command> behavior(EntityContext<Command> context);

    /**
     * Whether the shard should recreate this type's entities after a rebalance or a cluster restart.
     *
     * <p>True suits an entity that stands for something long-lived, where losing it loses the thing.
     * Override to false where entity ids are unbounded — one per unit of work rather than one per
     * durable thing — because the remembered set then grows with every id ever used, and each entry
     * costs a write to the remember-entities store.
     */
    protected boolean rememberEntities() {
        return true;
    }

    public EntityRef<Command> entityRef(final String id) {
        return actorSystemProvider.entityRefFor(entityTypeKey, id);
    }
}
