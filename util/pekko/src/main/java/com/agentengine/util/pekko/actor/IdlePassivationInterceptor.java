package com.agentengine.util.pekko.actor;

import java.time.Duration;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.BehaviorInterceptor;
import org.apache.pekko.actor.typed.TypedActorContext;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;

/**
 * Wraps an entity's behavior with self-managed idle passivation: starts a timer on start, resets it
 * on every message, and on expiry sends {@link ClusterSharding.Passivate} to the shard instead of
 * forwarding the timeout to the wrapped behavior.
 *
 * <p>Built for {@code rememberEntities=true} entity types, where Pekko's built-in idle-passivation
 * strategy is silently disabled (Pekko Cluster Sharding's docs, "Automatic Passivation" section:
 * "Enabling remembered entities disables Automatic Passivation"). Manually sending {@code
 * Passivate} is unaffected by that restriction and is what actually drops an idle entity out of the
 * remembered set — dying any other way leaves it remembered, so it gets recreated on the next
 * rebalance or recovery.
 *
 * @param <Command> the entity's command type
 */
public final class IdlePassivationInterceptor<Command>
    extends BehaviorInterceptor<Command, Command> {

  private final Command idleTimeoutCommand;
  private final Duration idleTimeout;
  private final ActorRef<ClusterSharding.ShardCommand> shard;

  // Not volatile: only ever touched from aroundStart/aroundReceive, and Pekko never calls those
  // concurrently for one actor instance - same reasoning as MessageFaultInterceptor's own state.
  private Cancellable idleTimer;

  /**
   * @param commandClass the entity's command type, for interceptor registration
   * @param idleTimeoutCommand the message we schedule and watch for — when it comes back, we know
   *     the entity's been idle long enough. Never actually reaches the wrapped behavior, so any
   *     command instance works, it's only ever compared by identity.
   * @param idleTimeout how long without a message before this entity passivates
   * @param shard this entity's shard, obtained from {@code EntityContext.getShard()} at entity
   *     creation time — where the {@code Passivate} command must be sent
   */
  public IdlePassivationInterceptor(
      final Class<Command> commandClass,
      final Command idleTimeoutCommand,
      final Duration idleTimeout,
      final ActorRef<ClusterSharding.ShardCommand> shard) {
    super(commandClass);
    this.idleTimeoutCommand = idleTimeoutCommand;
    this.idleTimeout = idleTimeout;
    this.shard = shard;
  }

  @Override
  public Behavior<Command> aroundStart(
      final TypedActorContext<Command> ctx, final PreStartTarget<Command> target) {
    final Behavior<Command> started = target.start(ctx);
    rescheduleIdleTimer(ctx);
    return started;
  }

  @Override
  public Behavior<Command> aroundReceive(
      final TypedActorContext<Command> ctx,
      final Command msg,
      final ReceiveTarget<Command> target) {
    if (msg == idleTimeoutCommand) {
      shard.tell(new ClusterSharding.Passivate<>(ctx.asJava().getSelf()));
      return Behaviors.same();
    }
    rescheduleIdleTimer(ctx);
    return target.apply(ctx, msg);
  }

  private void rescheduleIdleTimer(final TypedActorContext<Command> ctx) {
    if (idleTimer != null) {
      idleTimer.cancel();
    }
    final ActorContext<Command> actorContext = ctx.asJava();
    idleTimer = actorContext.scheduleOnce(idleTimeout, actorContext.getSelf(), idleTimeoutCommand);
  }
}
