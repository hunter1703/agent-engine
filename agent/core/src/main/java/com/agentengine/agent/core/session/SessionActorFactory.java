package com.agentengine.agent.core.session;

import com.agentengine.agent.core.factories.RunnerFactory;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.actor.ShardedEntityFactory;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

@Singleton
@Unremovable
public class SessionActorFactory extends ShardedEntityFactory<SessionCommand> {

    public static final Duration ASK_TIMEOUT = Duration.ofSeconds(10);

    @Inject
    public SessionActorFactory(
            final ActorSystemProvider actorSystemProvider,
            final SessionEventChannel sessionEventChannel,
            final RunnerFactory runnerFactory,
            final SessionService sessionService,
            final SessionTitleGenerator sessionTitleGenerator) {
        super(
                actorSystemProvider,
                SessionActor.TYPE_KEY,
                entityContext -> Behaviors.setup(actorCtx -> new SessionActor(
                        actorCtx,
                        entityContext.getEntityId(),
                        actorSystemProvider.pekkoConfig().getSnapshotThreshold(),
                        sessionEventChannel,
                        (sessionId) -> actorSystemProvider.entityRefFor(SessionActor.TYPE_KEY, sessionId),
                        runnerFactory,
                        sessionService,
                        sessionTitleGenerator)),
                Duration.ofHours(1),
                "runtime");
    }
}
