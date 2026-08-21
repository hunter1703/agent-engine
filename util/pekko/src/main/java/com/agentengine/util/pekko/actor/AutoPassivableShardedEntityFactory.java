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
 * — see {@link RememberingPassivableShardedEntityFactory} for the other case — so {@link
 * #rememberEntities()} is fixed to {@code false} here, closing off the combination that would
 * otherwise silently disable it (Pekko Cluster Sharding's docs, "Automatic Passivation" section:
 * "Enabling remembered entities disables Automatic Passivation").
 *
 * <p>Subclasses supply {@link #domainBehavior(EntityContext)} instead of {@link
 * #behavior(EntityContext)}; there's nothing to add here since Pekko's strategy is applied to the
 * sharding settings rather than the behavior.
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
    ClusterShardingSettings shardingSettings = super.shardingSettings(system);
    if (!rememberEntities) {
      shardingSettings =
          shardingSettings.withPassivationStrategy(
              ClusterShardingSettings.PassivationStrategySettings$.MODULE$
                  .defaults()
                  .withIdleEntityPassivation(passivationTimeout));
    }
    return shardingSettings;
  }
}
