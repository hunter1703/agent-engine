package com.agentengine.util.pekko.actor;

import com.agentengine.util.pekko.ActorSystemProvider;
import java.time.Duration;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.ClusterShardingSettings;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * A {@link ShardedEntityFactory} whose entities passivate after a period of inactivity, via Pekko's
 * own built-in idle-passivation strategy. That strategy only works when entities aren't remembered
 * — see {@link RememberedPassivableShardedEntityFactory} for the other case — so remember-entities
 * is fixed to {@code false} here, closing off the combination that would otherwise silently disable
 * it (Pekko Cluster Sharding's docs, "Automatic Passivation" section: "Enabling remembered entities
 * disables Automatic Passivation").
 *
 * <p>Subclasses implement {@link #behavior(EntityContext)} directly, same as the base class — this
 * only adds the passivation strategy to the sharding settings.
 *
 * @param <Command> the entity's command type
 */
public abstract class AutoPassivableShardedEntityFactory<Command>
    extends ShardedEntityFactory<Command> {

  private final Duration passivationTimeout;

  protected AutoPassivableShardedEntityFactory(
      final ActorSystemProvider actorSystemProvider,
      final EntityTypeKey<Command> entityTypeKey,
      final Duration passivationTimeout,
      final String role) {
    super(actorSystemProvider, entityTypeKey, role, false);
    this.passivationTimeout = passivationTimeout;
  }

  @Override
  protected final ClusterShardingSettings shardingSettings(final ActorSystem<?> system) {
    return super.shardingSettings(system)
        .withPassivationStrategy(
            ClusterShardingSettings.PassivationStrategySettings$.MODULE$
                .defaults()
                .withIdleEntityPassivation(passivationTimeout));
  }
}
