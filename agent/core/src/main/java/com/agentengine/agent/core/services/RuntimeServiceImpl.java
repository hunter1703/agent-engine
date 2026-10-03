package com.agentengine.agent.core.services;

import static com.agentengine.util.common.Defaults.STREAMING_BATCH_SIZE;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.api.services.CommunityExpertsService;
import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.agent.core.memory.MemoryRepository;
import com.agentengine.agent.core.schedule.AgentScheduleRepository;
import com.agentengine.agent.core.session.CurrentTurnEvents;
import com.agentengine.agent.core.session.InitializeResult;
import com.agentengine.agent.core.session.ResumeResult;
import com.agentengine.agent.core.session.RollbackResult;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.SessionEventChannel;
import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.core.session.commands.ExternalCommand.DeleteCommand;
import com.agentengine.agent.core.session.commands.ExternalCommand.GetCurrentTurnEventsCommand;
import com.agentengine.agent.core.session.commands.ExternalCommand.ResumeCommand;
import com.agentengine.agent.core.session.commands.ExternalCommand.RollbackCommand;
import com.agentengine.agent.core.session.commands.ExternalCommand.StartCommand;
import com.agentengine.agent.core.session.commands.ParentCommand.InitializeCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.core.session.state.SessionTopology;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.session.SessionEventsRepository;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.knowledge.api.beans.IndexRequest;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.runner.SchedulerService;
import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.agui.AGUIEventMapper;
import com.agentengine.util.agents.beans.AgentSchedule;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.beans.session.SessionStatus;
import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.utils.StructuredConcurrencyUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionedCache;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope.Subtask;
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

  /**
   * Attachments at or below this size are granted as a raw knowledge file (read whole, on demand).
   * Larger text attachments are indexed instead, since a large file is both wasteful to hand the
   * model in full and a better fit for semantic search.
   */
  private static final long INDEXING_THRESHOLD_BYTES = 5 * 1024;

  private static final String INVOKE_AGENT_JOB_CLASS_NAME =
      "com.agentengine.agent.jobs.InvokeAgentJob";

  private final SessionActorFactory sessionActorFactory;
  private final SessionEventChannel eventChannel;
  private final SessionService sessionService;
  private final PermissionedCache<AgentSession> sessionCache;
  private final PermissionedCache<BaseAgentConfig> agentCache;
  private final SessionEventsRepository sessionEventsRepository;
  private final KnowledgeService knowledgeService;
  private final CloudStorageService cloudStorageService;
  private final CommunityExpertsService communityExpertsService;
  private final AgentScheduleRepository agentScheduleRepository;
  private final MemoryRepository memoryRepository;
  private final NotebookRepository notebookRepository;
  private final SchedulerService schedulerService;
  private final AccessControlService accessControlService;

  @Inject
  public RuntimeServiceImpl(
      final SessionActorFactory sessionActorFactory,
      final SessionEventChannel eventChannel,
      final SessionService sessionService,
      final PermissionedCache<AgentSession> sessionCache,
      final PermissionedCache<BaseAgentConfig> agentCache,
      final SessionEventsRepository sessionEventsRepository,
      final KnowledgeService knowledgeService,
      final CloudStorageService cloudStorageService,
      final CommunityExpertsService communityExpertsService,
      final AgentScheduleRepository agentScheduleRepository,
      final MemoryRepository memoryRepository,
      final NotebookRepository notebookRepository,
      final SchedulerService schedulerService,
      final AccessControlService accessControlService) {
    this.sessionActorFactory = sessionActorFactory;
    this.eventChannel = eventChannel;
    this.sessionService = sessionService;
    this.sessionCache = sessionCache;
    this.agentCache = agentCache;
    this.sessionEventsRepository = sessionEventsRepository;
    this.knowledgeService = knowledgeService;
    this.cloudStorageService = cloudStorageService;
    this.communityExpertsService = communityExpertsService;
    this.agentScheduleRepository = agentScheduleRepository;
    this.memoryRepository = memoryRepository;
    this.notebookRepository = notebookRepository;
    this.schedulerService = schedulerService;
    this.accessControlService = accessControlService;
  }

  @Override
  public Map<String, Acl> getAcls(final String assetClass, final Collection<String> assetIds) {
    return switch (assetClass) {
      case AssetClass.AGENT_SCHEDULE -> agentScheduleRepository.readAcls(assetIds);
      case AssetClass.MEMORY -> memoryRepository.readAcls(assetIds);
      case AssetClass.NOTEBOOK -> notebookRepository.readAcls(assetIds);
      default -> throw new ConfigurationException("No repository serves asset class " + assetClass);
    };
  }

  @Override
  public Set<String> applyAcls(final String assetClass, final Map<String, Acl> assetIdVsAcl) {
    return switch (assetClass) {
      case AssetClass.AGENT_SCHEDULE -> agentScheduleRepository.applyAcls(assetIdVsAcl);
      case AssetClass.MEMORY -> memoryRepository.applyAcls(assetIdVsAcl);
      case AssetClass.NOTEBOOK -> notebookRepository.applyAcls(assetIdVsAcl);
      default -> throw new ConfigurationException("No repository serves asset class " + assetClass);
    };
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
    final AgentSession session = sessionCache.get(resolvedSessionId);
    if (session == null) {
      throw new AssetNotFoundException(AssetClass.AGENT_SESSION, resolvedSessionId);
    }
    final String rootSessionId =
        StringUtils.isNotBlank(session.getRootSessionId())
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

  /**
   * Initializes the session, a new one when {@code sessionId} is blank or names none, and returns
   * its id. An existing session takes EDIT on it, and must be of {@code agentId}; a deleted one's
   * id can never be used again.
   */
  private String initializeSession(final String agentId, final String sessionId) {
    requireAgentAccess(agentId);
    if (StringUtils.isNotBlank(sessionId)) {
      requireCanContinueSession(agentId, sessionId);
    }
    final String resolvedSessionId =
        StringUtils.isBlank(sessionId) ? SessionUtils.newSessionId(agentId) : sessionId;
    final InitializeResult result =
        sessionActorFactory
            .entityRef(resolvedSessionId)
            .<InitializeResult>ask(
                replyTo ->
                    new InitializeCommand(
                        SessionTopology.root(agentId, resolvedSessionId), replyTo),
                SessionActorFactory.ASK_TIMEOUT)
            .toCompletableFuture()
            .join(); // block until the session is persisted
    if (result.deleted()) {
      throw new AssetNotFoundException(AssetClass.AGENT_SESSION, resolvedSessionId);
    }
    return resolvedSessionId;
  }

  private void requireAgentAccess(final String agentId) {
    if (agentCache.get(agentId) == null) {
      throw new UnauthorizedException(AssetClass.AGENT, agentId);
    }
  }

  private void requireCanEditSession(final String sessionId) {
    if (!sessionService.hasPermission(sessionId, Permission.EDIT)) {
      throw new UnauthorizedException(AssetClass.AGENT_SESSION, sessionId);
    }
  }

  /**
   * Throws unless no session has the id yet, or the caller may edit the session and it is of {@code
   * agentId}. The session is read as the system, since whether it exists is not the caller's to
   * see, and uncached, since a session missing now is about to be created.
   */
  private void requireCanContinueSession(final String agentId, final String sessionId) {
    final AgentSession session =
        Context.require()
            .asSystemCaller()
            .get(() -> sessionService.getSession(sessionId, List.of(AgentSession.FIELD_AGENT_ID)));
    if (session == null) {
      return;
    }
    requireCanEditSession(sessionId);
    if (!agentId.equals(session.getAgentId())) {
      throw new IllegalArgumentException(
          "Session " + sessionId + " is of agent " + session.getAgentId() + ", not " + agentId);
    }
  }

  private void startTurn(final String agentId, final String sessionId, final UserMessage message) {
    LOG.debug("Starting session {}:{}", agentId, sessionId);
    final UserMessage resolvedMessage = resolveMessage(agentId, sessionId, message);
    sessionActorFactory
        .entityRef(sessionId)
        .<StartSessionResult>ask(
            replyTo -> new StartCommand(new UniqueRecord<>(resolvedMessage), replyTo),
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

  private UserMessage resolveMessage(
      final String agentId, final String sessionId, final UserMessage message) {
    return Context.require()
        .actingAs(AgentSession.principal(agentId, sessionId))
        .get(
            () -> {
              final List<AgentFileDetails> attachments = message.attachments();
              if (CollectionUtils.isEmpty(attachments)) {
                return message;
              }

              final List<AgentFileDetails> toIndex = new ArrayList<>();
              final List<AgentFileDetails> resolved = new ArrayList<>();
              for (final AgentFileDetails fileDetails : attachments) {
                if (needsIndexing(fileDetails)) {
                  toIndex.add(fileDetails);
                } else {
                  resolved.add(createUnindexedKnowledge(agentId, fileDetails));
                }
              }

              final List<Callable<String>> indexing =
                  toIndex.stream()
                      .<Callable<String>>map(
                          fileDetails -> () -> indexAsKnowledge(agentId, fileDetails).getId())
                      .toList();
              final List<StructuredConcurrencyUtils.TaskOutcome<String>> outcomes =
                  StructuredConcurrencyUtils.callConcurrentlyUntil(
                      "knowledge-indexing", indexing, _ -> false);

              for (final StructuredConcurrencyUtils.TaskOutcome<String> outcome : outcomes) {
                if (outcome.state() == Subtask.State.SUCCESS) {
                  resolved.add(toIndex.get(outcome.index()).withKnowledgeId(outcome.value()));
                } else {
                  LOG.warn(
                      "Indexing failed for {}; dropping it from the message's attachments",
                      toIndex.get(outcome.index()).source(),
                      outcome.error());
                }
              }

              return new UserMessage(message.parts(), resolved);
            });
  }

  private AgentFileDetails createUnindexedKnowledge(
      final String agentId, final AgentFileDetails fileDetails) {
    final Knowledge knowledge = createKnowledge(agentId, fileDetails, true);
    return fileDetails.withKnowledgeId(knowledge.getId());
  }

  private boolean needsIndexing(final AgentFileDetails agentFileDetails) {
    final FileDetails fileDetails = agentFileDetails.toFileDetails();
    if (!FileUtils.isTextFile(fileDetails)
        && !FileUtils.isOfficeFile(fileDetails)
        && !FileUtils.isPdfFile(fileDetails)
        && !FileUtils.isImageFile(fileDetails)) {
      return false;
    }
    final long size =
        agentFileDetails.size() < 1
            ? cloudStorageService.getSize(agentFileDetails.source())
            : agentFileDetails.size();
    return size > INDEXING_THRESHOLD_BYTES;
  }

  private Knowledge indexAsKnowledge(final String agentId, final AgentFileDetails fileDetails) {
    return createKnowledge(agentId, fileDetails, false);
  }

  private Knowledge createKnowledge(
      final String agentId, final AgentFileDetails fileDetails, final boolean skipIndexing) {
    final IndexRequest request = new IndexRequest();
    request.setAgentId(agentId);
    request.setFileDetails(fileDetails.toFileDetails());
    request.setTitle(fileDetails.name());
    request.setSkipIndexing(skipIndexing);
    request.setWaitForCompletion(true);
    return knowledgeService.create(request);
  }

  @Override
  public void resumeSession(final String sessionId, final ResumeRequest resumeRequest) {
    LOG.debug(
        "Resuming session {} with interrupt id '{}'", sessionId, resumeRequest.getInterruptId());
    requireCanEditSession(sessionId);
    requireAgentAccess(sessionCache.get(sessionId).getAgentId());
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
    requireCanEditSession(sessionId);
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

  /**
   * Retires the session's actor first, so no run starts while it goes, then has tenancy forget what
   * was shared with the session, then deletes its record.
   */
  @Override
  public boolean deleteSession(final String sessionId) {
    if (!sessionService.hasPermission(sessionId, Permission.DELETE)) {
      return false;
    }
    final AgentSession session =
        Context.require().asSystemCaller().get(() -> sessionCache.get(sessionId));
    sessionActorFactory
        .entityRef(sessionId)
        .<Done>ask(DeleteCommand::new, SessionActorFactory.ASK_TIMEOUT)
        .toCompletableFuture()
        .join();
    Context.require()
        .asSystemCaller()
        .run(
            () ->
                accessControlService.forgetPrincipal(
                    AgentSession.principal(session.getAgentId(), sessionId).toString()));
    return sessionService.deleteSession(sessionId);
  }

  @Override
  public String invokeExpert(
      final String expertId, final String modelId, final UserMessage userMessage) {
    return communityExpertsService.invokeExpert(expertId, modelId, userMessage);
  }

  @Override
  public AgentSchedule saveSchedule(final AgentSchedule schedule) {
    requireAgentAccess(schedule.getAgentId());
    final AgentSchedule saved = agentScheduleRepository.save(schedule);
    final JobDefinition existingJob = schedulerService.getJob(saved.getId());
    schedulerService.schedule(
        invokeAgentJob(existingJob == null ? new JobDefinition() : existingJob, saved));
    return saved;
  }

  @Override
  public AgentSchedule getSchedule(final String id) {
    return agentScheduleRepository.findById(id);
  }

  @Override
  public Map<String, AgentSchedule> getSchedules(final Collection<String> ids) {
    return agentScheduleRepository.findByIds(ids);
  }

  @Override
  public PaginatedResult<AgentSchedule> findSchedules(final Query query) {
    return agentScheduleRepository.findByQuery(query);
  }

  @Override
  public boolean deleteSchedule(final String id) {
    if (!agentScheduleRepository.deleteByIdIgnoringVersion(id)) {
      return false;
    }
    schedulerService.cancelJob(id);
    return true;
  }

  @Override
  public void deleteAgentSchedules(final Collection<String> agentIds) {
    final List<AgentSchedule> schedules =
        agentScheduleRepository
            .findByQuery(
                new Query()
                    .withFilter(Filters.in(AgentSchedule.FIELD_AGENT_ID, List.copyOf(agentIds)))
                    .withIncludeFields(List.of(BaseEntity.FIELD_ID))
                    .withPage(Page.UNBOUNDED))
            .getItems();
    for (final AgentSchedule schedule : schedules) {
      deleteSchedule(schedule.getId());
    }
  }

  @Override
  public Publisher<SessionEvent> subscribeToSession(
      final String sessionId, final boolean liveOnly) {
    final AgentSession session =
        sessionService.getSession(
            sessionId, List.of(AgentSession.FIELD_ROOT_SESSION_ID, AgentSession.FIELD_STATUS));
    if (session == null) {
      throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
    }
    final String rootSessionId = session.getRootSessionId();

    // Completed or failed sessions: liveOnly subscribers get nothing (they missed all events);
    // replay subscribers get the full history.
    if (isTerminalStatus(session.getStatus())) {
      return liveOnly ? Flowable.empty() : pagedCommittedEvents(rootSessionId);
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
    final AgentSession reChecked =
        sessionService.getSession(
            sessionId, List.of(AgentSession.FIELD_ROOT_SESSION_ID, AgentSession.FIELD_STATUS));
    if (reChecked != null && isTerminalStatus(reChecked.getStatus())) {
      liveConnection.dispose();
      return liveOnly ? Flowable.empty() : pagedCommittedEvents(reChecked.getRootSessionId());
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
        pagedCommittedEvents(rootSessionId)
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
    final AgentSession session =
        sessionService.getSession(
            sessionId,
            List.of(AgentSession.FIELD_ROOT_SESSION_ID, AgentSession.FIELD_ROOT_AGENT_ID));
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

  private Flowable<SessionEvent> pagedCommittedEvents(final String rootSessionId) {
    return fetchCommittedEventsPage(rootSessionId, 0);
  }

  private Flowable<SessionEvent> fetchCommittedEventsPage(
      final String rootSessionId, final int offset) {
    return Flowable.fromSupplier(
            () ->
                sessionEventsRepository.getCommittedEvents(
                    rootSessionId, true, new Page(offset, STREAMING_BATCH_SIZE)))
        .flatMap(
            (PaginatedResult<SessionEvent> page) -> {
              final Flowable<SessionEvent> items = Flowable.fromIterable(page.getItems());
              return page.getItems().size() < STREAMING_BATCH_SIZE
                  ? items
                  : items.concatWith(
                      Flowable.defer(
                          () ->
                              fetchCommittedEventsPage(
                                  rootSessionId, offset + STREAMING_BATCH_SIZE)));
            });
  }

  private Flowable<SessionEvent> subscribeToLiveEvents(final String rootSessionId) {
    // EventChannel only guarantees at-least-once delivery (see its javadoc) -- dedup by stable
    // ADK event ID, same as subscribeToSession's liveEvents, so a redelivered event doesn't get
    // double-counted by SessionEventUtils.compactEventStream's naive text concatenation.
    final Set<String> seen = ConcurrentHashMap.newKeySet();
    final ConnectableFlowable<SessionEvent> liveSource =
        Flowable.fromPublisher(
                eventChannel.subscribe(rootSessionId).toCompletableFuture().join().publisher())
            .map(SequencedEvent::payload)
            .cast(SessionEvent.class)
            .filter(event -> seen.add(event.getId()))
            .takeWhile(event -> !event.isTerminal())
            .replay();
    final Disposable connection = liveSource.connect();
    return liveSource.doFinally(connection::dispose);
  }

  private List<SessionEvent> getCurrentTurnEvents(final String sessionId) {
    final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(sessionId);
    return ref.<CurrentTurnEvents>ask(
            GetCurrentTurnEventsCommand::new, SessionActorFactory.ASK_TIMEOUT)
        .toCompletableFuture()
        .join()
        .events();
  }

  /**
   * Sets {@code job}, the stored job of {@code schedule} or a new one, to invoke the schedule's
   * agent on its cron; everything else the job carries is kept.
   */
  private static JobDefinition invokeAgentJob(
      final JobDefinition job, final AgentSchedule schedule) {
    job.setId(schedule.getId());
    job.setJobClassName(INVOKE_AGENT_JOB_CLASS_NAME);
    job.setCronSchedule(schedule.getCronSchedule());
    job.setPayload(
        Map.of(
            "agentId", schedule.getAgentId(),
            "message", schedule.getMessage(),
            "singletonSession", schedule.isSingletonSession()));
    return job;
  }

  private record StartedSession(String resolvedSessionId, Publisher<SessionEvent> liveEvents) {}
}
