package com.agentengine.runtime.session;

import com.agentengine.runtime.factories.RunnerFactory;
import com.agentengine.runtime.services.MongoSessionService;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.actor.ShardedEntityFactory;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

@Singleton
@Unremovable
public class SessionActorFactory extends ShardedEntityFactory<SessionCommand> {

    public static final Duration ASK_TIMEOUT = Duration.ofSeconds(10);

    @Inject
    public SessionActorFactory(
            final ActorSystemProvider actorSystemProvider,
            final SessionEventChannel sessionEventChannel,
            final RunnerFactory runnerFactory,
            final MongoSessionService sessionService) {
        super(
                actorSystemProvider,
                SessionActor.TYPE_KEY,
                entityContext -> Behaviors.setup(actorCtx -> new SessionActor(
                        actorCtx,
                        entityContext.getEntityId(),
                        actorSystemProvider.pekkoConfig().getSnapshotThreshold(),
                        sessionEventChannel,
                        (agentId, sessionId) ->
                                actorSystemProvider.entityRefFor(SessionActor.TYPE_KEY, agentId + ":" + sessionId),
                        runnerFactory,
                        sessionService)),
                Duration.ofHours(1),
                "runtime");
    }

    public EntityRef<SessionCommand> entityRef(final String agentId, final String sessionId) {
        return entityRef(agentId + ":" + sessionId);
    }
}
