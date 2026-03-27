package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.actor.ShardedEntityFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

import java.time.Duration;

@Singleton
public class SessionActorFactory extends ShardedEntityFactory<SessionCommand> {

    public static final Duration ASK_TIMEOUT = Duration.ofSeconds(10);

    @Inject
    public SessionActorFactory(final ActorSystem<SpawnProtocol.Command> actorSystem,
                               final SessionEventChannel sessionEventChannel,
                               final AgentRunner runner) {
        super(
            actorSystem,
            SessionActor.TYPE_KEY,
            ctx -> Behaviors.setup(actorCtx -> new SessionActor(actorCtx, ctx.getEntityId(), runner, sessionEventChannel)),
            Duration.ofHours(1));
    }

    public EntityRef<SessionCommand> entityRef(final String agentId, final String sessionId) {
        return entityRef(agentId + ":" + sessionId);
    }
}
