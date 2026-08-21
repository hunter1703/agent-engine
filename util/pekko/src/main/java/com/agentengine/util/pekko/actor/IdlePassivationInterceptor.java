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
 * Wraps a cluster-sharded entity's behavior with self-managed idle passivation: starts an idle
 * timer the moment the actor starts, resets it on every message, and on expiry sends {@link
 * ClusterSharding.Passivate} to the shard instead of letting the timeout reach the wrapped
 * behavior.
 *
 * <p>Its main use case is a {@code rememberEntities=true} entity type: enabling remember-entities
 * silently disables the built-in auto passivation entirely (Pekko Cluster Sharding's docs,
 * "Automatic Passivation" section — "Enabling remembered entities disables Automatic Passivation"),
 * so for an entity type with an unbounded id space that also needs remember-entities (to resume a
 * genuinely in-progress entity after a crash), every id ever used would otherwise stay in the
 * remembered set forever, and get recreated — including long-idle ones — on every shard rebalance
 * or crash recovery. Manually sending {@code Passivate} is a separate code path in Pekko's {@code
 * Shard}, unaffected by that restriction: it explicitly removes the entity from the durable
 * remembered-entities store once it terminates this way (as opposed to just dying, which leaves it
 * remembered so it restarts) — so only entities still genuinely active, never passivated, remain
 * remembered.
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
