package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.ConfirmResult;
import com.agentengine.runtime.actor.RuntimeService;
import com.agentengine.runtime.actor.StartSessionResult;
import com.agentengine.runtime.session.SessionActorFactory;
import com.agentengine.runtime.session.commands.ExternalCommand;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.session.state.SessionTopology;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.beans.UniqueRecord;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletionStage;
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
    public CompletionStage<StartSessionResult> startSession(final String agentId, final String sessionId, final String message) {
        LOG.info("Starting session {}:{}", agentId, sessionId);
        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(agentId, sessionId);
        final SessionTopology topology = SessionTopology.root(agentId, sessionId);
        return ref.<Done>ask(
                        replyTo -> new ExternalCommand.InitializeCommand(topology, replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .thenCompose(ignored -> ref.<StartSessionResult>ask(
                        replyTo -> new ExternalCommand.StartCommand(new UniqueRecord<>(message), replyTo), SessionActorFactory.ASK_TIMEOUT))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to start session {}:{}", agentId, sessionId, ex);
                    } else {
                        LOG.info("Session {}:{} start result: {}", agentId, sessionId, result);
                    }
                });
    }

    @Override
    public CompletionStage<ConfirmResult> confirmSession(
            final String agentId,
            final String sessionId,
            final Confirmation confirmation) {
        LOG.info("Resuming session {}:{} with confirmation '{}'", agentId, sessionId, confirmation.getConfirmationId());
        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(agentId, sessionId);
        return ref.<ConfirmResult>ask(
                        replyTo -> new ExternalCommand.ConfirmCommand(confirmation, replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to resume session {}:{}", agentId, sessionId, ex);
                    } else {
                        LOG.info("Session {}:{} resume result: {}", agentId, sessionId, result);
                    }
                });
    }
}
