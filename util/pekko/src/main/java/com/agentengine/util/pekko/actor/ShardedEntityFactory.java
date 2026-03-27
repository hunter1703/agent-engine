package com.agentengine.util.pekko.actor;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingSettings;
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.japi.function.Function;

import java.time.Duration;

/**
 * Base factory for cluster-sharded entities. Handles sharding registration and entity ref
 * acquisition. The {@code builder} function receives the {@link EntityContext} and should
 * return a {@link Behavior} — typically a {@code Behaviors.setup()} wrapper so that the
 * entity can receive an {@code ActorContext} in its constructor.
 *
 * @param <Command> the entity's command type
 */
public abstract class ShardedEntityFactory<Command> implements ShardedEntity.ShardedEntityDefinition<Command, ShardingEnvelope<Command>> {

    private final ActorSystem<SpawnProtocol.Command> actorSystem;
    private Entity<Command, ShardingEnvelope<Command>> entity;

    protected ShardedEntityFactory(final ActorSystem<SpawnProtocol.Command> actorSystem,
                                   final EntityTypeKey<Command> entityTypeKey,
                                   final Function<EntityContext<Command>, Behavior<Command>> builder, final Duration passivationDuration) {
        this.actorSystem = actorSystem;
        this.entity = buildEntity(entityTypeKey, builder, passivationDuration);
    }

    @Override
    public Entity<Command, ShardingEnvelope<Command>> entity() {
        return entity;
    }

    public EntityRef<Command> entityRef(final String id) {
        return ClusterSharding.get(actorSystem).entityRefFor(entity.typeKey(), id);
    }

    private Entity<Command, ShardingEnvelope<Command>> buildEntity(final EntityTypeKey<Command> entityTypeKey, final Function<EntityContext<Command>, Behavior<Command>> builder, final Duration passivationDuration) {
        final Entity<Command, ShardingEnvelope<Command>> entity = Entity.of(entityTypeKey, builder);
        if (passivationDuration != null) {
            final ClusterShardingSettings settings = ClusterShardingSettings.create(actorSystem).withPassivationStrategy(
                    ClusterShardingSettings.PassivationStrategySettings$.MODULE$.defaults().withIdleEntityPassivation(passivationDuration));
            return entity.withSettings(settings);
        }
        return entity;
    }
}
