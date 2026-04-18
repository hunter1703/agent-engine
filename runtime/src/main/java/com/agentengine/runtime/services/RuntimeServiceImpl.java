package com.agentengine.runtime.services;

import com.agentengine.core.api.services.SessionService;
import com.agentengine.runtime.api.model.UserMessage;
import com.agentengine.runtime.api.services.RuntimeService;
import com.agentengine.runtime.api.services.SessionHistoryService;
import com.agentengine.runtime.session.ConfirmResult;
import com.agentengine.runtime.session.SessionActorFactory;
import com.agentengine.runtime.session.SessionEventChannel;
import com.agentengine.runtime.session.StartSessionResult;
import com.agentengine.runtime.session.commands.ExternalCommand.ConfirmCommand;
import com.agentengine.runtime.session.commands.ExternalCommand.GetCurrentTurnEventsCommand;
import com.agentengine.runtime.session.commands.ExternalCommand.StartCommand;
import com.agentengine.runtime.session.commands.ParentCommand.InitializeCommand;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.session.state.SessionTopology;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.beans.session.SessionStatus;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.StructuredConcurrencyUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.common.exception.AssetNotFoundException;
import io.quarkus.arc.Unremovable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.flowables.ConnectableFlowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final SessionHistoryService sessionHistoryService;

    @Inject
    public RuntimeServiceImpl(
            final SessionActorFactory sessionActorFactory,
            final SessionEventChannel eventChannel,
            final SessionService sessionService,
            final SessionHistoryService sessionHistoryService) {
        this.sessionActorFactory = sessionActorFactory;
        this.eventChannel = eventChannel;
        this.sessionService = sessionService;
        this.sessionHistoryService = sessionHistoryService;
    }

    @Override
    public String startSession(final String agentId, final String sessionId, final UserMessage message) {
        final String resolvedSessionId =
                StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
        LOG.info("Starting session {}:{}", agentId, resolvedSessionId);

        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(resolvedSessionId);
        ref.<Done>ask(
                        replyTo -> new InitializeCommand(SessionTopology.root(agentId, resolvedSessionId), replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .toCompletableFuture()
                .join(); // block until the session is persisted — safe to return the ID after this

        ref.<StartSessionResult>ask(
                        replyTo -> new StartCommand(new UniqueRecord<>(message), replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to start session {}:{}", agentId, resolvedSessionId, ex);
                    } else {
                        LOG.info("Session {}:{} start result: {}", agentId, resolvedSessionId, result);
                    }
                });

        return resolvedSessionId;
    }

    @Override
    public void confirmSession(final String sessionId, final Confirmation confirmation) {
        LOG.info("Confirming session {} with id '{}'", sessionId, confirmation.getConfirmationId());
        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(sessionId);
        ref.<ConfirmResult>ask(replyTo -> new ConfirmCommand(confirmation, replyTo), SessionActorFactory.ASK_TIMEOUT)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Failed to confirm session {}", sessionId, ex);
                    } else {
                        LOG.info("Session {} confirm result: {}", sessionId, result);
                    }
                });
    }

    @Override
    public Publisher<SessionEvent> subscribeToSession(final String sessionId) {
        final AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
        }
        final String rootSessionId = session.getRootSessionId();

        // Completed or failed sessions have no live events pending — emit history and close.
        if (isTerminalStatus(session.getStatus())) {
            return terminalStream(rootSessionId);
        }

        // Connect eagerly so the SubscriberActor is registered with the broadcaster BEFORE we
        // fetch history. Without this, events emitted while history/turn-events are being fetched
        // (and during the time the returned Flowable is being consumed by the SSE client) would
        // fall into a gap: not yet in committed history, no longer in turn events, and not captured
        // by a live subscription that hasn't started yet.
        // replay() records every event from connect() time; when liveFlow is eventually subscribed
        // (after history and turn events are exhausted), it replays the buffered events first and
        // then continues live. The dedup set eliminates any overlap with history/turn events.
        final ConnectableFlowable<SessionEvent> liveSource = Flowable.fromPublisher(eventChannel
                        .subscribe(rootSessionId)
                        .toCompletableFuture()
                        .join()
                        .publisher())
                .map(SequencedEvent::payload)
                .cast(SessionEvent.class)
                .replay();
        final Disposable liveConnection = liveSource.connect();

        //  re-check status after connect(). The initial status check and connect() are
        // not atomic — the session could have completed in the window between them, publishing its
        // terminal event before our SubscriberActor registered. Because SessionActor writes
        // COMPLETED status to MongoDB *before* publishing the terminal event, a fresh read of
        // COMPLETED here guarantees we missed the terminal; fall back to the history fast-path.
        final AgentSession reChecked = sessionService.getSession(sessionId);
        if (reChecked != null && isTerminalStatus(reChecked.getStatus())) {
            liveConnection.dispose();
            return terminalStream(reChecked.getRootSessionId());
        }

        // Fetch committed history and current turn events in parallel on virtual threads.
        final List<List<SessionEvent>> fetched = StructuredConcurrencyUtils.runConcurrently(List.of(
                () -> sessionHistoryService.getAllSessionEvents(rootSessionId), () -> getCurrentTurnEvents(sessionId)));
        final List<SessionEvent> history = fetched.get(0);
        final List<SessionEvent> turnEvents = fetched.get(1);

        // Dedup by stable ADK event ID — same event has the same ID across all three layers.
        final Set<String> seen = ConcurrentHashMap.newKeySet();
        return Flowable.concat(
                Flowable.fromIterable(history).filter(event -> seen.add(event.getId())),
                Flowable.fromIterable(turnEvents).filter(event -> seen.add(event.getId())),
                // if liveSource completes without a terminal event (broadcaster stopped,
                // actor crash, or passivation), check status and synthesize the terminal so the SSE
                // client is never left hanging on a stream that will never close.
                liveSource.filter(event -> seen.add(event.getId())).takeWhile(event -> !event.isTerminal()));
    }

    private static boolean isTerminalStatus(final SessionStatus status) {
        return status == SessionStatus.COMPLETED || status == SessionStatus.FAILED;
    }

    private Flowable<SessionEvent> terminalStream(final String rootSessionId) {
        return Flowable.fromIterable(sessionHistoryService.getAllSessionEvents(rootSessionId));
    }

    private List<SessionEvent> getCurrentTurnEvents(final String sessionId) {
        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(sessionId);
        return ref.ask(GetCurrentTurnEventsCommand::new, SessionActorFactory.ASK_TIMEOUT)
                .toCompletableFuture()
                .join()
                .events();
    }
}
