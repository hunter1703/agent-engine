package com.agentengine.runtime.services;

import com.agentengine.core.api.services.SessionService;
import com.agentengine.runtime.api.services.RuntimeService;
import com.agentengine.runtime.session.ConfirmResult;
import com.agentengine.runtime.session.SessionActorFactory;
import com.agentengine.runtime.session.SessionEventChannel;
import com.agentengine.runtime.session.StartSessionResult;
import com.agentengine.runtime.session.commands.ExternalCommand;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.session.state.SessionTopology;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.common.events.EventSubscription;
import com.agentengine.util.common.events.SequencedEvent;
import io.quarkus.arc.Unremovable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Predicate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.UUID;
import org.apache.pekko.Done;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class RuntimeServiceImpl implements RuntimeService {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeServiceImpl.class);

    private final SessionActorFactory sessionActorFactory;
    private final SessionEventChannel eventChannel;
    private final SessionService sessionService;

    @Inject
    public RuntimeServiceImpl(
            final SessionActorFactory sessionActorFactory,
            final SessionEventChannel eventChannel,
            final SessionService sessionService) {
        this.sessionActorFactory = sessionActorFactory;
        this.eventChannel = eventChannel;
        this.sessionService = sessionService;
    }

    @Override
    public Publisher<SessionEvent> startSession(final String agentId, final String sessionId, final String message) {
        final String resolvedSessionId = StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
        LOG.info("Starting session {}:{}", agentId, resolvedSessionId);

        // Subscribe before sending commands so no events are missed during the startup window.
        final EventSubscription<SequencedEvent<SessionEvent>> subscription =
                eventChannel.subscribe(resolvedSessionId).toCompletableFuture().join();

        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(resolvedSessionId);
        ref.<Done>ask(
                        replyTo -> new ExternalCommand.InitializeCommand(
                                SessionTopology.root(agentId, resolvedSessionId), replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .thenCompose(ignored -> ref.<StartSessionResult>ask(
                        replyTo -> new ExternalCommand.StartCommand(new UniqueRecord<>(message), replyTo),
                        SessionActorFactory.ASK_TIMEOUT))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to start session {}:{}", agentId, resolvedSessionId, ex);
                    } else {
                        LOG.info("Session {}:{} start result: {}", agentId, resolvedSessionId, result);
                    }
                });

        return getSubscribedEvents(subscription, resolvedSessionId);
    }

    @Override
    public Publisher<SessionEvent> confirmSession(final String sessionId, final Confirmation confirmation) {
        LOG.info("Confirming session {} with id '{}'", sessionId, confirmation.getConfirmationId());

        final AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            return Flowable.error(new IllegalArgumentException("Session not found: " + sessionId));
        }

        final String rootSessionId = session.getRootSessionId();

        // Subscribe before sending the confirm command so the resumed event stream is not missed.
        final EventSubscription<SequencedEvent<SessionEvent>> subscription =
                eventChannel.subscribe(rootSessionId).toCompletableFuture().join();

        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(sessionId);
        ref.<ConfirmResult>ask(
                        replyTo -> new ExternalCommand.ConfirmCommand(confirmation, replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to confirm session {}", sessionId, ex);
                    } else {
                        LOG.info("Session {} confirm result: {}", sessionId, result);
                    }
                });

        return getSubscribedEvents(subscription, rootSessionId);
    }

    private static Flowable<SessionEvent> getSubscribedEvents(final EventSubscription<SequencedEvent<SessionEvent>> subscription, final String rootSessionId) {
        return Flowable.fromPublisher(subscription.publisher())
                .map(SequencedEvent::payload)
                .cast(SessionEvent.class)
                .takeUntil(sessionEvent -> Objects.equals(rootSessionId, sessionEvent.getSessionId()) && sessionEvent.isTerminal())
                .doFinally(subscription::cancel);
    }
}
