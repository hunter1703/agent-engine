package com.agentengine.agent.core.services;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.agent.core.session.ResumeResult;
import com.agentengine.agent.core.session.RollbackResult;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.SessionEventChannel;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.ExternalCommand.GetCurrentTurnEventsCommand;
import com.agentengine.agent.core.session.commands.ExternalCommand.ResumeCommand;
import com.agentengine.agent.core.session.commands.ExternalCommand.RollbackCommand;
import com.agentengine.agent.core.session.commands.ExternalCommand.StartCommand;
import com.agentengine.agent.core.session.commands.ParentCommand.InitializeCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.core.session.state.SessionTopology;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.agui.AGUIEventMapper;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.beans.session.SessionStatus;
import com.agentengine.util.agents.repository.SessionEventsRepository;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.community.core.event.Event;
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

  private static final long COMPACTION_WINDOW_MILLIS = 100;

  private final SessionActorFactory sessionActorFactory;
  private final SessionEventChannel eventChannel;
  private final SessionService sessionService;
  private final SessionEventsRepository sessionEventsRepository;

  @Inject
  public RuntimeServiceImpl(
      final SessionActorFactory sessionActorFactory,
      final SessionEventChannel eventChannel,
      final SessionService sessionService,
      final SessionEventsRepository sessionEventsRepository) {
    this.sessionActorFactory = sessionActorFactory;
    this.eventChannel = eventChannel;
    this.sessionService = sessionService;
    this.sessionEventsRepository = sessionEventsRepository;
  }

  @Override
  public Publisher<SessionEvent> startSession(
      final String agentId, final String sessionId, final UserMessage message) {
    return startSessionInternal(agentId, sessionId, message).liveEvents();
  }

  @Override
  public Publisher<Event> startSessionAgui(
      final String agentId, final String sessionId, final UserMessage message) {
    final StartedSession started = startSessionInternal(agentId, sessionId, message);
    final AGUIEventMapper mapper = new AGUIEventMapper(started.resolvedSessionId(), agentId);
    return mapper.map(Flowable.fromPublisher(started.liveEvents()));
  }

  private StartedSession startSessionInternal(
      final String agentId, final String sessionId, final UserMessage message) {
    final String resolvedSessionId = initializeSession(agentId, sessionId);
    final AgentSession session = sessionService.getSession(resolvedSessionId);
    final String rootSessionId =
        session != null && StringUtils.isNotBlank(session.getRootSessionId())
            ? session.getRootSessionId()
            : resolvedSessionId;
    // Reusing an existing session is a new turn, not a replay subscription. The previous turn
    // may have left the persisted session status as COMPLETED, so subscribe directly to the
    // live root-session channel before starting the run.
    final Publisher<SessionEvent> liveEvents =
        SessionEventUtils.compactEventStream(
            subscribeToLiveEvents(rootSessionId), COMPACTION_WINDOW_MILLIS);
    startTurn(agentId, resolvedSessionId, message);
    return new StartedSession(resolvedSessionId, liveEvents);
  }

  @Override
  public String invoke(final String agentId, final String sessionId, final UserMessage message) {
    final String initializedSessionId = initializeSession(agentId, sessionId);
    startTurn(agentId, initializedSessionId, message);
    return initializedSessionId;
  }

  private String initializeSession(final String agentId, final String sessionId) {
    final String resolvedSessionId =
        StringUtils.isBlank(sessionId) ? SessionUtils.newSessionId(agentId) : sessionId;
    sessionActorFactory
        .entityRef(resolvedSessionId)
        .<Done>ask(
            replyTo ->
                new InitializeCommand(SessionTopology.root(agentId, resolvedSessionId), replyTo),
            SessionActorFactory.ASK_TIMEOUT)
        .toCompletableFuture()
        .join(); // block until the session is persisted
    return resolvedSessionId;
  }

  private void startTurn(final String agentId, final String sessionId, final UserMessage message) {
    LOG.debug("Starting session {}:{}", agentId, sessionId);
    sessionActorFactory
        .entityRef(sessionId)
        .<StartSessionResult>ask(
            replyTo -> new StartCommand(new UniqueRecord<>(message), replyTo),
            SessionActorFactory.ASK_TIMEOUT)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                LOG.error("Failed to start session {}:{}", agentId, sessionId, ex);
              } else {
                LOG.debug("Session {}:{} start result: {}", agentId, sessionId, result);
              }
            });
  }

  @Override
  public void resumeSession(final String sessionId, final ResumeRequest resumeRequest) {
    LOG.debug(
        "Resuming session {} with interrupt id '{}'", sessionId, resumeRequest.getInterruptId());
    final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(sessionId);
    ref.<ResumeResult>ask(
            replyTo -> new ResumeCommand(resumeRequest, replyTo), SessionActorFactory.ASK_TIMEOUT)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                LOG.error("Failed to resume session {}", sessionId, ex);
              } else {
                LOG.debug("Session {} resume result: {}", sessionId, result);
              }
            });
  }

  @Override
  public void rollbackSession(final String sessionId, final String runId) {
    LOG.debug("Rolling back run {} for session {}", runId, sessionId);
    final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(sessionId);
    final RollbackResult result =
        ref.<RollbackResult>ask(
                replyTo -> new RollbackCommand(runId, replyTo), SessionActorFactory.ASK_TIMEOUT)
            .toCompletableFuture()
            .join();
    if (result instanceof RollbackResult.Rejected(String reason)) {
      throw new IllegalStateException("Rollback rejected: " + reason);
    }
  }

  @Override
  public Publisher<SessionEvent> subscribeToSession(
      final String sessionId, final boolean liveOnly) {
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
    final ConnectableFlowable<SessionEvent> liveSource =
        Flowable.fromPublisher(
                eventChannel.subscribe(rootSessionId).toCompletableFuture().join().publisher())
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
    final Flowable<SessionEvent> liveEvents =
        liveSource.filter(event -> seen.add(event.getId())).takeWhile(event -> !event.isTerminal());

    if (liveOnly) {
      return SessionEventUtils.compactEventStream(liveEvents, COMPACTION_WINDOW_MILLIS)
          .doFinally(liveConnection::dispose);
    }

    // Fetch committed history and current turn events lazily.
    final Flowable<SessionEvent> committedEvents =
        Flowable.fromSupplier(
                () -> sessionEventsRepository.getCommittedSessionEvents(rootSessionId, true))
            .flatMapIterable(list -> list)
            .filter(event -> seen.add(event.getId()))
            .map(RuntimeServiceImpl::stripBlobData);

    final Flowable<SessionEvent> uncommittedEvents =
        Flowable.fromSupplier(() -> getCurrentTurnEvents(sessionId))
            .flatMapIterable(list -> list)
            .filter(event -> seen.add(event.getId()))
            .map(RuntimeServiceImpl::stripBlobData);

    // A session that's still running never has a fixed end to wait for, so it always gets the
    // windowed compaction over the whole combined stream, committed history included: this is one
    // continuous stream a still-active session might keep appending to, and treating the
    // history/live boundary as a hard compaction break would under-merge a message that's still
    // streaming across it. Windowing the (already-coalesced-at-commit, near-instantly-flowing)
    // history part costs little — there's nothing left to merge there — while correctly bounding
    // the live tail's added latency.
    final Flowable<SessionEvent> combined =
        Flowable.concat(
            committedEvents,
            uncommittedEvents,
            Flowable.just(SessionEvent.liveMarker(rootSessionId)),
            liveEvents);
    return SessionEventUtils.compactEventStream(combined, COMPACTION_WINDOW_MILLIS)
        .doFinally(liveConnection::dispose);
  }

  @Override
  public Publisher<Event> subscribeToSessionAgui(final String sessionId, final boolean liveOnly) {
    final AgentSession session = sessionService.getSession(sessionId);
    if (session == null) {
      throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
    }
    final AGUIEventMapper mapper =
        new AGUIEventMapper(session.getRootSessionId(), session.getRootAgentId());
    return mapper.map(Flowable.fromPublisher(subscribeToSession(sessionId, liveOnly)));
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
        sanitizedParts.add(part.toBuilder().inlineData(blob.toBuilder().clearData()).build());
      } else {
        sanitizedParts.add(part);
      }
    }
    final Content sanitizedContent = content.toBuilder().parts(sanitizedParts).build();
    event.setRawEvent(event.getRawEvent().toBuilder().content(sanitizedContent).build());
    return event;
  }

  private static boolean isTerminalStatus(final SessionStatus status) {
    return status == SessionStatus.COMPLETED || status == SessionStatus.FAILED;
  }

  private Flowable<SessionEvent> terminalStream(final String rootSessionId) {
    final List<SessionEvent> events =
        sessionEventsRepository.getCommittedSessionEvents(rootSessionId, true);
    return Flowable.fromIterable(events);
  }

  private Flowable<SessionEvent> subscribeToLiveEvents(final String rootSessionId) {
    final ConnectableFlowable<SessionEvent> liveSource =
        Flowable.fromPublisher(
                eventChannel.subscribe(rootSessionId).toCompletableFuture().join().publisher())
            .map(SequencedEvent::payload)
            .cast(SessionEvent.class)
            .takeWhile(event -> !event.isTerminal())
            .replay();
    final Disposable connection = liveSource.connect();
    return liveSource.doFinally(connection::dispose);
  }

  private List<SessionEvent> getCurrentTurnEvents(final String sessionId) {
    final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(sessionId);
    return ref.ask(GetCurrentTurnEventsCommand::new, SessionActorFactory.ASK_TIMEOUT)
        .toCompletableFuture()
        .join()
        .events();
  }

  private record StartedSession(String resolvedSessionId, Publisher<SessionEvent> liveEvents) {}
}
