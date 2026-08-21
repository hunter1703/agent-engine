package com.agentengine.util.pekko.actor;

import com.agentengine.util.pekko.ActorSystemProvider;
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
 * <p>Says nothing about passivation — a subclass that wants its entities to unload when idle
 * extends {@link AutoPassivableShardedEntityFactory} or {@link
 * RememberedPassivableShardedEntityFactory} instead.
 *
 * @param <Command> the entity's command type
 */
public abstract class ShardedEntityFactory<Command> implements ShardedEntityDefinition {

  private final ActorSystemProvider actorSystemProvider;
  private final EntityTypeKey<Command> entityTypeKey;
  private final String role;
  protected final boolean rememberEntities;

  protected ShardedEntityFactory(
      final ActorSystemProvider actorSystemProvider,
      final EntityTypeKey<Command> entityTypeKey,
      final String role,
      boolean rememberEntities) {
    this.actorSystemProvider = actorSystemProvider;
    this.entityTypeKey = entityTypeKey;
    this.role = role;
    this.rememberEntities = rememberEntities;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <M, E> Entity<M, E> entity(final ActorSystem<?> system) {
    Entity<Command, ShardingEnvelope<Command>> entity =
        Entity.of(entityTypeKey, this::behavior).withSettings(shardingSettings(system));
    if (role != null) {
      entity = entity.withRole(role);
    }
    return (Entity<M, E>) entity;
  }

  public EntityRef<Command> entityRef(final String id) {
    return actorSystemProvider.entityRefFor(entityTypeKey, id);
  }

  /**
   * Hook for a subclass to layer on more settings — {@link AutoPassivableShardedEntityFactory} adds
   * Pekko's built-in idle-passivation strategy here for the one case it actually works in.
   */
  protected ClusterShardingSettings shardingSettings(final ActorSystem<?> system) {
    return ClusterShardingSettings.create(system).withRememberEntities(rememberEntities);
  }

  /**
   * Builds the behaviour for one entity. A method rather than a constructor argument so that a
   * subclass can use its own fields — anything passed to {@code super(...)} has to exist before the
   * subclass's fields are assigned, which forces its collaborators to be built somewhere else.
   */
  protected abstract Behavior<Command> behavior(EntityContext<Command> context);
}
