package com.agentengine.agent.core.services;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.agent.api.services.SessionHistoryService;
import com.agentengine.agent.core.session.ConfirmResult;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.SessionEventChannel;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.ExternalCommand.ConfirmCommand;
import com.agentengine.agent.core.session.commands.ExternalCommand.GetCurrentTurnEventsCommand;
import com.agentengine.agent.core.session.commands.ExternalCommand.StartCommand;
import com.agentengine.agent.core.session.commands.ParentCommand.InitializeCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.core.session.state.SessionTopology;
import com.agentengine.catalog.api.services.SessionService;
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
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.quarkus.arc.Unremovable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.flowables.ConnectableFlowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.*;
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
    public Publisher<SessionEvent> startSession(
            final String agentId, final String sessionId, final UserMessage message) {
        final String resolvedSessionId =
                StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
        LOG.info("Starting session {}:{}", agentId, resolvedSessionId);

        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(resolvedSessionId);
        ref.<Done>ask(
                        replyTo -> new InitializeCommand(SessionTopology.root(agentId, resolvedSessionId), replyTo),
                        SessionActorFactory.ASK_TIMEOUT)
                .toCompletableFuture()
                .join(); // block until the session is persisted — safe to subscribe after this
        final AgentSession session = sessionService.getSession(resolvedSessionId);
        final String rootSessionId = session != null && StringUtils.isNotBlank(session.getRootSessionId())
                ? session.getRootSessionId()
                : resolvedSessionId;
        // Reusing an existing session is a new turn, not a replay subscription. The previous turn
        // may have left the persisted session status as COMPLETED, so subscribe directly to the
        // live root-session channel before starting the run.
        final Publisher<SessionEvent> liveEvents = subscribeToLiveEvents(rootSessionId);

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

        return liveEvents;
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
    public Publisher<SessionEvent> subscribeToSession(final String sessionId, final boolean liveOnly) {
        final AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
        }
        final String rootSessionId = session.getRootSessionId();

        // Completed or failed sessions: liveOnly subscribers get nothing (they missed all events);
        // replay subscribers get the full history.
        if (isTerminalStatus(session.getStatus())) {
            return liveOnly ? Flowable.empty() : terminalStream(rootSessionId);
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
            return liveOnly ? Flowable.empty() : terminalStream(reChecked.getRootSessionId());
        }

        // Dedup by stable ADK event ID — same event has the same ID across all three layers.
        final Set<String> seen = ConcurrentHashMap.newKeySet();
        final Flowable<SessionEvent> filteredLiveSource =
                liveSource.filter(event -> seen.add(event.getId())).takeWhile(event -> !event.isTerminal());

        if (liveOnly) {
            return filteredLiveSource;
        }

        // Fetch committed history and current turn events in parallel on virtual threads.
        final List<List<SessionEvent>> fetched = StructuredConcurrencyUtils.runConcurrently(List.of(
                () -> sessionHistoryService.getAllSessionEvents(rootSessionId), () -> getCurrentTurnEvents(sessionId)));
        final List<SessionEvent> history = fetched.get(0);
        final List<SessionEvent> turnEvents = fetched.get(1);

        return Flowable.concat(
                Flowable.fromIterable(history)
                        .filter(event -> seen.add(event.getId()))
                        .map(RuntimeServiceImpl::stripBlobData),
                Flowable.fromIterable(turnEvents)
                        .filter(event -> seen.add(event.getId()))
                        .map(RuntimeServiceImpl::stripBlobData),
                Flowable.just(SessionEvent.liveMarker(rootSessionId)),
                filteredLiveSource);
    }

    private static SessionEvent stripBlobData(final SessionEvent event) {
        final Content content = event.getContent();
        if (content == null) {
            return event;
        }

        final List<Part> parts = content.parts().orElse(List.of());

        final List<Part> sanitizedParts = new ArrayList<>();
        for (final Part part : parts) {
            final Optional<Blob> blobOptional = part.inlineData();
            if (blobOptional.isPresent()) {
                final Blob blob = blobOptional.get();
                sanitizedParts.add(part.toBuilder()
                        .inlineData(blob.toBuilder().clearData())
                        .build());
            } else {
                sanitizedParts.add(part);
            }
        }
        event.setContent(content.toBuilder().parts(sanitizedParts).build());
        return event;
    }

    private static boolean isTerminalStatus(final SessionStatus status) {
        return status == SessionStatus.COMPLETED || status == SessionStatus.FAILED;
    }

    private Flowable<SessionEvent> terminalStream(final String rootSessionId) {
        return Flowable.fromIterable(sessionHistoryService.getAllSessionEvents(rootSessionId));
    }

    private Flowable<SessionEvent> subscribeToLiveEvents(final String rootSessionId) {
        return Flowable.fromPublisher(eventChannel
                        .subscribe(rootSessionId)
                        .toCompletableFuture()
                        .join()
                        .publisher())
                .map(SequencedEvent::payload)
                .cast(SessionEvent.class)
                .takeWhile(event -> !event.isTerminal());
    }

    private List<SessionEvent> getCurrentTurnEvents(final String sessionId) {
        final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(sessionId);
        return ref.ask(GetCurrentTurnEventsCommand::new, SessionActorFactory.ASK_TIMEOUT)
                .toCompletableFuture()
                .join()
                .events();
    }
}
