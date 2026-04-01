package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.ResumeResult;
import com.agentengine.runtime.actor.RuntimeService;
import com.agentengine.runtime.actor.StartSessionResult;
import com.agentengine.runtime.session.SessionActorFactory;
import com.agentengine.runtime.session.commands.ExternalCommand;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.session.state.SessionTopology;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.Done;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class RuntimeServiceImpl implements RuntimeService {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeServiceImpl.class);

    private final SessionActorFactory sessionActorFactory;

    @Inject
    public RuntimeServiceImpl(final SessionActorFactory sessionActorFactory) {
        this.sessionActorFactory = sessionActorFactory;
    }

    @Override
    public StartSessionResult startSession(final String agentId, final String sessionId, final String message) {
        LOG.info("Starting session {}:{}", agentId, sessionId);
        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(agentId, sessionId);
        final SessionTopology topology = SessionTopology.root(agentId, sessionId);
        return ref.<Done>ask(
                        replyTo -> new ExternalCommand.InitializeCommand(topology, replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .thenCompose(ignored -> ref.<StartSessionResult>ask(
                        replyTo -> new ExternalCommand.StartCommand(message, replyTo), SessionActorFactory.ASK_TIMEOUT))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to start session {}:{}", agentId, sessionId, ex);
                    } else {
                        LOG.info("Session {}:{} start result: {}", agentId, sessionId, result);
                    }
                })
                .toCompletableFuture()
                .join();
    }

    @Override
    public ResumeResult resumeSession(
            final String agentId,
            final String sessionId,
            final String confirmationId,
            final boolean confirmed,
            final String answer) {
        LOG.info("Resuming session {}:{} with confirmation '{}'", agentId, sessionId, confirmationId);
        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(agentId, sessionId);
        return ref.<ResumeResult>ask(
                        replyTo -> new ExternalCommand.ResumeCommand(confirmationId, confirmed, answer, replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to resume session {}:{}", agentId, sessionId, ex);
                    } else {
                        LOG.info("Session {}:{} resume result: {}", agentId, sessionId, result);
                    }
                })
                .toCompletableFuture()
                .join();
    }
}
