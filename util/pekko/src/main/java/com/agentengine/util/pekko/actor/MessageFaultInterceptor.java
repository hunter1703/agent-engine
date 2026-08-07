package com.agentengine.util.pekko.actor;

import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.BehaviorInterceptor;
import org.apache.pekko.actor.typed.TypedActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Wraps a sharded entity's behavior to drop or delay messages according to whatever {@link
 * ChaosMailboxConfig} is currently registered for {@code entityId} in {@link
 * ChaosMailboxRegistry}. The registry is consulted fresh on every message, so activating or
 * clearing a fault takes effect immediately without an actor restart. Lifecycle signals are never
 * intercepted — only {@code aroundReceive} is overridden.
 *
 * <p>A delayed message is redelivered via {@code self.tell()}, which re-enters this same
 * interceptor. Without tracking that redelivery, a still-active delay config would delay it again
 * forever. {@code inFlightRedeliveries} (identity-based, since a single actor processes one
 * message at a time — no synchronization needed) marks a message as "already delayed once" so its
 * redelivery passes straight through instead of re-matching the config.
 *
 * @param <Command> the entity's command type
 */
public final class MessageFaultInterceptor<Command> extends BehaviorInterceptor<Command, Command> {

    private final String entityId;
    private final ChaosMailboxRegistry registry;
    private final Set<Command> inFlightRedeliveries = Collections.newSetFromMap(new IdentityHashMap<>());

    public MessageFaultInterceptor(
            final Class<Command> commandClass, final String entityId, final ChaosMailboxRegistry registry) {
        super(commandClass);
        this.entityId = entityId;
        this.registry = registry;
    }

    @Override
    public Behavior<Command> aroundReceive(
            final TypedActorContext<Command> ctx, final Command msg, final ReceiveTarget<Command> target) {
        if (inFlightRedeliveries.remove(msg)) {
            return target.apply(ctx, msg);
        }

        final Optional<ChaosMailboxConfig> config = registry.configFor(entityId);
        if (config.isEmpty()) {
            return target.apply(ctx, msg);
        }

        final ChaosMailboxConfig mailboxConfig = config.get();
        if (mailboxConfig.shouldDrop()) {
            return Behaviors.same();
        }
        if (mailboxConfig.shouldDelay()) {
            final Duration delay = mailboxConfig.delayDuration().orElseThrow();
            inFlightRedeliveries.add(msg);
            ctx.asJava().scheduleOnce(delay, ctx.asJava().getSelf(), msg);
            return Behaviors.same();
        }
        return target.apply(ctx, msg);
    }
}
