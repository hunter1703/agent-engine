package com.agentengine.runtime.session;

import com.agentengine.runtime.actor.SessionEventChannel;
import com.agentengine.runtime.factories.RunnerFactory;
import com.agentengine.runtime.services.MongoSessionService;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.util.pekko.PekkoConfig;
import com.agentengine.util.pekko.actor.ShardedEntityFactory;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.function.BiFunction;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

@Singleton
@Unremovable
public class SessionActorFactory extends ShardedEntityFactory<SessionCommand> {

    public static final Duration ASK_TIMEOUT = Duration.ofSeconds(10);

    @Inject
    public SessionActorFactory(
            final ActorSystem<SpawnProtocol.Command> actorSystem,
            final PekkoConfig pekkoConfig,
            final SessionEventChannel sessionEventChannel,
            final RunnerFactory runnerFactory,
            final MongoSessionService sessionService) {
        super(
                actorSystem,
                SessionActor.TYPE_KEY,
                entityContext -> Behaviors.setup(actorCtx -> new SessionActor(
                        actorCtx,
                        entityContext.getEntityId(),
                        pekkoConfig.getSnapshotThreshold(),
                        sessionEventChannel,
                        refSupplier(actorSystem),
                        runnerFactory,
                        sessionService)),
                Duration.ofHours(1));
    }

    private static BiFunction<String, String, EntityRef<SessionCommand>> refSupplier(
            final ActorSystem<SpawnProtocol.Command> actorSystem) {
        final ClusterSharding sharding = ClusterSharding.get(actorSystem);
        return (agentId, sessionId) -> sharding.entityRefFor(SessionActor.TYPE_KEY, agentId + ":" + sessionId);
    }

    public EntityRef<SessionCommand> entityRef(final String agentId, final String sessionId) {
        return entityRef(agentId + ":" + sessionId);
    }
}
