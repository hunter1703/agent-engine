package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.ActorUtils;
import com.agentengine.runtime.actor.SessionActor;
import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.actor.services.RuntimeService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@Singleton
public class RuntimeServiceImpl implements RuntimeService {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeServiceImpl.class);
    private final SessionActorFactory sessionActorFactory;

    @Inject
    public RuntimeServiceImpl(final SessionActorFactory sessionActorFactory) {
        this.sessionActorFactory = sessionActorFactory;
    }

    @Override
    public CompletionStage<SessionActor.RunReceipt> startSession(final String agentId,
                                                                  final String sessionId,
                                                                  final String message) {
        LOG.info("Starting session {}:{}", agentId, sessionId);
        final EntityRef<SessionActor.Command> ref = sessionActorFactory.entityRef(agentId, sessionId);
        return ref.<SessionActor.RunReceipt>ask(
                        replyTo -> new SessionActor.Command.StartRun(message, replyTo), ActorUtils.DEFAULT_ASK_TIMEOUT)
                .whenComplete((receipt, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to start session {}:{}", agentId, sessionId, ex);
                    } else {
                        LOG.info("Session {}:{} start result: {}", agentId, sessionId, receipt);
                    }
                });
    }

    @Override
    public CompletionStage<SessionActor.RunReceipt> resumeSession(final String agentId,
                                                                   final String sessionId,
                                                                   final String confirmationId,
                                                                   final boolean confirmed,
                                                                   final String answer) {
        LOG.info("Resuming session {}:{} with confirmation '{}'", agentId, sessionId, confirmationId);
        final EntityRef<SessionActor.Command> ref = sessionActorFactory.entityRef(agentId, sessionId);
        return ref.<SessionActor.RunReceipt>ask(
                        replyTo -> new SessionActor.Command.ResumeRun(confirmationId, confirmed, answer, replyTo), ActorUtils.DEFAULT_ASK_TIMEOUT)
                .whenComplete((receipt, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to resume session {}:{}", agentId, sessionId, ex);
                    } else {
                        LOG.info("Session {}:{} resume result: {}", agentId, sessionId, receipt);
                    }
                });
    }
}
