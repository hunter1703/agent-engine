package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.actor.BroadcastBehavior;
import com.agentengine.util.pekko.actor.ShardedEntity;
import com.agentengine.util.pekko.events.PekkoEventChannel;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.stream.Materializer;
import org.reactivestreams.Publisher;

/**
 * Cluster-wide event channel for session actor events.
 *
 * <p>
 * Each agent graph (session tree) is isolated by its {@code rootSessionId} — events published
 * for one root session are never delivered to subscribers of another. Isolation is enforced by
 * Pekko cluster sharding: each {@code rootSessionId} maps to a distinct
 * {@link BroadcastBehavior} shard, completely independent of other shards within the same
 * registered entity type.
 *
 * <p>
 * Being a CDI {@link Singleton} that implements {@link ShardedEntity.ShardedEntityDefinition},
 * this bean is auto-discovered by {@code ActorSystemProvider} and registered with cluster
 * sharding at startup — before any publish or subscribe call is made.
 */
@Singleton
public class SessionEventChannel
        implements ShardedEntity.ShardedEntityDefinition<BroadcastBehavior.Command, ShardingEnvelope<BroadcastBehavior.Command>> {

    private final PekkoEventChannel<String, SessionEvent> channel;

    public SessionEventChannel(final ActorSystem<?> system, final Materializer materializer) {
        this.channel = new PekkoEventChannel<>(system, materializer, "session-events");
    }

    /**
     * Publishes an event to all subscribers of the given root session's stream.
     *
     * @param rootSessionId the root session ID of the agent graph
     * @param event         the session event to publish
     */
    public void publish(final String rootSessionId, final SessionEvent event) {
        channel.publish(rootSessionId, event);
    }

    /**
     * Returns a live {@link Publisher} of events for the given root session's stream.
     * Only events published after subscription is established are delivered.
     *
     * @param rootSessionId the root session ID of the agent graph
     */
    public Publisher<SessionEvent> events(final String rootSessionId) {
        return channel.events(rootSessionId);
    }

    /**
     * Signals end-of-stream for the given root session. All subscribers receive a normal
     * completion signal.
     *
     * @param rootSessionId the root session ID whose stream should be terminated
     */
    public void complete(final String rootSessionId) {
        channel.complete(rootSessionId);
    }

    @Override
    public Entity<BroadcastBehavior.Command, ShardingEnvelope<BroadcastBehavior.Command>> entity() {
        return channel.entity();
    }
}
