package com.agentengine.util.pekko.actor;

import com.agentengine.util.pekko.ActorSystemProvider;
import java.time.Duration;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * A {@link ShardedEntityFactory} whose entities are both remembered — a genuinely in-progress
 * entity must resume after a crash or rebalance — and passivate after a period of inactivity.
 *
 * <p>Pekko's built-in idle-passivation strategy is silently disabled whenever remember-entities is
 * on (Pekko Cluster Sharding's docs, "Automatic Passivation" section: "Enabling remembered entities
 * disables Automatic Passivation") — see {@link AutoPassivableShardedEntityFactory} for the case
 * where that strategy applies instead. {@link IdlePassivationInterceptor} stands in for it here,
 * manually sending {@code Passivate}: a separate code path in Pekko's {@code Shard}, unaffected by
 * that restriction, and the only way to get idle entities to actually drop out of the remembered
 * set instead of being recreated forever.
 *
 * <p>Subclasses supply {@link #domainBehavior(EntityContext)} instead of {@link
 * #behavior(EntityContext)} — this class wraps it with the interceptor itself.
 *
 * @param <Command> the entity's command type
 */
public abstract class RememberedPassivableShardedEntityFactory<Command>
    extends ShardedEntityFactory<Command> {

  private final Duration passivationTimeout;
  private final Class<Command> commandType;

  protected RememberedPassivableShardedEntityFactory(
      final ActorSystemProvider actorSystemProvider,
      final EntityTypeKey<Command> entityTypeKey,
      final Duration passivationTimeout,
      final String role,
      final Class<Command> commandType) {
    super(actorSystemProvider, entityTypeKey, role, true);
    this.passivationTimeout = passivationTimeout;
    this.commandType = commandType;
  }

  @Override
  protected final Behavior<Command> behavior(final EntityContext<Command> entityContext) {
    final Behavior<Command> behavior = domainBehavior(entityContext);
    return Behaviors.intercept(
        () ->
            new IdlePassivationInterceptor<>(
                commandType, idleTimeoutCommand(), passivationTimeout, entityContext.getShard()),
        behavior);
  }

  /** Same role {@link #behavior(EntityContext)} plays on the base class, minus passivation. */
  protected abstract Behavior<Command> domainBehavior(EntityContext<Command> entityContext);

  /**
   * A command instance used only as {@link IdlePassivationInterceptor}'s self-scheduled idle marker
   * — never actually reaches {@link #domainBehavior(EntityContext)}, so any instance works.
   */
  protected abstract Command idleTimeoutCommand();
}
