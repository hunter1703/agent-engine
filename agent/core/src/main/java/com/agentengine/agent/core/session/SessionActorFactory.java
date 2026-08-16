package com.agentengine.agent.core.session;

import com.agentengine.agent.core.factories.RunnerFactory;
import com.agentengine.agent.core.memory.MemoryService;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.actor.ChaosMailboxRegistry;
import com.agentengine.util.pekko.actor.MessageFaultInterceptor;
import com.agentengine.util.pekko.actor.ShardedEntityFactory;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;

@Singleton
@Unremovable
public class SessionActorFactory extends ShardedEntityFactory<SessionCommand> {

    public static final Duration ASK_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration PASSIVATION_TIMEOUT = Duration.ofHours(1);
    private static final String AGENT_ROLE = "agent";

    private final ActorSystemProvider actorSystemProvider;
    private final SessionEventChannel sessionEventChannel;
    private final RunnerFactory runnerFactory;
    private final SessionService sessionService;
    private final SessionTitleGenerator sessionTitleGenerator;
    private final MemoryService memoryService;
    private final ChaosMailboxRegistry chaosMailboxRegistry;

    @Inject
    public SessionActorFactory(
            final ActorSystemProvider actorSystemProvider,
            final SessionEventChannel sessionEventChannel,
            final RunnerFactory runnerFactory,
            final SessionService sessionService,
            final SessionTitleGenerator sessionTitleGenerator,
            final MemoryService memoryService,
            final ChaosMailboxRegistry chaosMailboxRegistry) {
        super(actorSystemProvider, SessionActor.TYPE_KEY, PASSIVATION_TIMEOUT, AGENT_ROLE);
        this.actorSystemProvider = actorSystemProvider;
        this.sessionEventChannel = sessionEventChannel;
        this.runnerFactory = runnerFactory;
        this.sessionService = sessionService;
        this.sessionTitleGenerator = sessionTitleGenerator;
        this.memoryService = memoryService;
        this.chaosMailboxRegistry = chaosMailboxRegistry;
    }

    @Override
    protected Behavior<SessionCommand> behavior(final EntityContext<SessionCommand> entityContext) {
        return Behaviors.intercept(
                () -> new MessageFaultInterceptor<>(
                        SessionCommand.class, entityContext.getEntityId(), chaosMailboxRegistry),
                Behaviors.setup(actorContext -> new SessionActor(
                        actorContext,
                        entityContext.getEntityId(),
                        actorSystemProvider.pekkoConfig().getSnapshotThreshold(),
                        sessionEventChannel,
                        sessionId -> actorSystemProvider.entityRefFor(SessionActor.TYPE_KEY, sessionId),
                        runnerFactory,
                        sessionService,
                        sessionTitleGenerator,
                        memoryService)));
    }
}
