package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.actor.ShardedEntityFactory;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

@Singleton
public class SessionActorFactory extends ShardedEntityFactory<SessionCommand> {

    public SessionActorFactory(final ActorSystem<Void> actorSystem,
                               final SessionEventChannel sessionEventChannel,
                               final AgentRunner runner) {
        super(actorSystem, SessionActor.TYPE_KEY, ctx ->
                Behaviors.setup(actorCtx ->
                        new SessionActor(actorCtx, ctx.getEntityId(), runner, sessionEventChannel)));
    }

    public EntityRef<SessionCommand> entityRef(final String agentId, final String sessionId) {
        return entityRef(agentId + ":" + sessionId);
    }
}
