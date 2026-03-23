package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.actor.ShardedEntityFactory;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

@Singleton
public class SessionActorFactory extends ShardedEntityFactory<SessionActor.Command> {

    public SessionActorFactory(final ActorSystem<Void> actorSystem,
                               final SessionEventChannel sessionEventChannel,
                               final AgentRunner runner) {
        super(actorSystem, SessionActor.getEntityTypeKey(), ctx -> {
            final String[] parts = ctx.getEntityId().split(":", 2);
            final String agentId = parts[0];
            final String sessionId = parts[1];
            return Behaviors.setup(actorCtx ->
                    new SessionActor(actorCtx, agentId, sessionId, sessionEventChannel, runner));
        });
    }

    public EntityRef<SessionActor.Command> entityRef(final String agentId, final String sessionId) {
        return entityRef(agentId + ":" + sessionId);
    }
}
