package com.agentengine.agent.core.session;

import static com.agentengine.agent.core.session.SessionActorFactory.ASK_TIMEOUT;
import static com.agentengine.util.agents.Constants.ToolArgs.ORIGINAL_FUNCTION_CALL;
import static com.agentengine.util.common.CollectionUtils.getValueFromMap;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.factories.RunnerFactory;
import com.agentengine.agent.core.memory.MemoryService;
import com.agentengine.agent.core.session.commands.ChildCommand.*;
import com.agentengine.agent.core.session.commands.ExternalCommand.*;
import com.agentengine.agent.core.session.commands.ParentCommand.*;
import com.agentengine.agent.core.session.commands.SelfCommand.*;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.core.session.events.*;
import com.agentengine.agent.core.session.state.*;
import com.agentengine.agent.core.tools.agent.AbstractAgentTool;
import com.agentengine.agent.core.tools.agent.AwaitAgentTool;
import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.beans.session.SessionStatus;
import com.agentengine.util.agents.repository.SessionEventsRepository;
import com.agentengine.util.common.*;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.pekko.actor.ShardedEntity;
import com.google.adk.events.Event;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import org.apache.pekko.Done;
import org.apache.pekko.actor.Scheduler;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.SnapshotAdapter;
import org.apache.pekko.persistence.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.ExecutionContextExecutor;

/** Persistent, cluster-sharded actor that manages a single agent session. */
public final class SessionActor
    extends ShardedEntity<SessionCommand, SessionFact, SessionActorState> {

  private static final Logger LOG = LoggerFactory.getLogger(SessionActor.class);

  public static final EntityTypeKey<SessionCommand> TYPE_KEY =
      EntityTypeKey.create(SessionCommand.class, AssetClass.AGENT_SESSION);

  private static final int MAX_CHILD_POLL_ATTEMPTS = 10;
  private static final Duration SELF_PAUSE_RETRY_INTERVAL = Duration.ofMinutes(1);
  private static final ExecutorService ASYNC_TASK_EXECUTOR =
      ThreadUtils.newVirtualThreadExecutor("session-async-task-");

  private final ActorContext<SessionCommand> context;
  private final ActorRef<SessionCommand> self;
  private final int snapshotThreshold;
  private final SessionEventChannel eventChannel;
  private final List<Event> turnEvents = new LinkedList<>();
  private String turnId;

  /**
   * Interrupt IDs already fed to {@link #runner}'s current instance via {@code runner.resume}, so a
   * later {@link #continueRun} within the same still-open turn does not hand it the same {@link
   * ResumeRequest} twice. Deliberately actor-local rather than persisted state: on recovery, {@code
   * runner} is rebuilt from scratch and has seen nothing yet, so this must also start empty then,
   * not be replayed as already-populated.
   */
  private final Set<String> resumedInterruptIds = new HashSet<>();

  private final java.util.function.Function<String, EntityRef<SessionCommand>> refSupplier;
  private final RunnerFactory runnerFactory;
  private final SessionService sessionService;
  private final SessionTitleGenerator sessionTitleGenerator;
  private final MemoryService memoryService;
  private final SessionEventsRepository sessionEventsRepository;
  private SessionRunner runner;

  public SessionActor(
      final ActorContext<SessionCommand> context,
      final String entityId,
      final int snapshotThreshold,
      final SessionEventChannel eventChannel,
      final java.util.function.Function<String, EntityRef<SessionCommand>> refSupplier,
      final RunnerFactory runnerFactory,
      final SessionService sessionService,
      final SessionTitleGenerator sessionTitleGenerator,
      final MemoryService memoryService,
      final SessionEventsRepository sessionEventsRepository) {
    super(TYPE_KEY.name(), entityId);
    this.context = context;
    this.self = context.getSelf();
    this.snapshotThreshold = snapshotThreshold;
    this.eventChannel = eventChannel;
    this.refSupplier = refSupplier;
    this.runnerFactory = runnerFactory;
    this.sessionService = sessionService;
    this.sessionTitleGenerator = sessionTitleGenerator;
    this.memoryService = memoryService;
    this.sessionEventsRepository = sessionEventsRepository;
  }

  @Override
  public SessionActorState emptyState() {
    return SessionActorState.initial();
  }

  @Override
  public SignalHandler<SessionActorState> signalHandler() {
    return newSignalHandlerBuilder()
        .onSignal(RecoveryCompleted.class, (state, _) -> onRecoveryCompleted(state))
        .onSignal(
            PostStop.class,
            (_, _) -> {
              if (runner != null) {
                runner.close();
              }
            })
        .build();
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    return RetentionCriteria.snapshotEvery(snapshotThreshold, 2);
  }

  /**
   * Isolates the snapshot from subsequent in-place mutations on the live state.
   *
   * <p>Per-event methods ({@code enqueue}, {@code dequeue}, etc.) mutate shared backing collections
   * for O(1) replay. {@link SessionActorState#copy()} produces fresh collection instances so the
   * persisted snapshot is never affected by later mutations.
   */
  @Override
  public SnapshotAdapter<SessionActorState> snapshotAdapter() {
    return new SnapshotAdapter<>() {
      @Override
      public Object toJournal(final SessionActorState state) {
        return state.copy();
      }

      @Override
      public SessionActorState fromJournal(final Object from) {
        return (SessionActorState) from;
      }
    };
  }

  private void onRecoveryCompleted(final SessionActorState state) {
    if (state == null) {
      return;
    }
    final SessionTopology topology = state.topology();
    if (topology == null) {
      return;
    }
    final SessionState sessionState = state.sessionState();
    final String sessionId = topology.sessionId();
    if (sessionState == SessionState.TRIGGERED_RUN) {
      final AgentSession session = sessionService.getSession(sessionId);
      if (session != null) {
        sessionService.deleteSession(sessionId);
      }
    }
    init(topology);
    // Redone unconditionally: a crash could have landed between RollbackFact persisting and this
    // Mongo write completing, and there's no record of whether it already succeeded. Safe to redo
    // regardless, since setting the same rollbackId twice is a no-op.
    final RolledBackRun lastRollback = state.lastRollback();
    if (lastRollback != null) {
      invalidateRolledBackEvents(state, lastRollback.runId(), lastRollback.sequence());
    }
    // Deletes any SessionEvent rows past nextSequence(): commitTurn's Mongo insert can outrun its
    // TurnCommittedFact persisting, leaving orphaned rows a crash never rolled back. Left alone,
    // the next legitimate commit would reuse that same sequence range and collide with them. Safe
    // to run unconditionally — nothing committed ever has sequence >= nextSequence(), so a normal
    // recovery matches zero rows.
    sessionEventsRepository.deleteByQuery(
        new Query()
            .withFilter(
                Filters.and(
                    Filters.eq(SessionEvent.FIELD_SESSION_ID, sessionId),
                    Filters.gte(SessionEvent.FIELD_SEQUENCE, state.nextSequence()))));
    final String rootSessionId = topology.rootSessionId();
    switch (sessionState) {
      case TRIGGERED_RUN -> {
        // The crash landed before the first turn committed, so restart from the original message.
        runner.start(
            Objects.requireNonNull(state.currentRun()).message().getRecord(), state.grants());
        updateSessionStatus(state, SessionStatus.RUNNING);
      }
      case CONTINUING -> {
        final Collection<ResumeRequest> resumeRequests = state.getAllReceivedResumes();
        resumeRequests.forEach(
            resumeRequest -> resumedInterruptIds.add(resumeRequest.getInterruptId()));
        runner.resume(resumeRequests, state.grants());
        updateSessionStatus(state, SessionStatus.RUNNING);
      }
      case RUNNING -> {
        final CommittedTurn lastCommittedTurn = state.lastCommittedTurn();
        final List<Event> lastCommitedEvents =
            lastCommittedTurn == null
                ? List.of()
                : sessionEventsRepository.findTurnEvents(sessionId, lastCommittedTurn.turnId());
        final int num = lastCommitedEvents.size();

        for (int i = 0; i < num; i++) {
          final SessionEvent sessionEvent =
              SessionEventUtils.toSessionEvent(
                  rootSessionId,
                  topology.parentSessionId(),
                  sessionId,
                  lastCommittedTurn.turnId(),
                  lastCommitedEvents.get(i),
                  state.nextSequence() - num + i);
          eventChannel.publish(rootSessionId, sessionEvent);
        }

        for (final StartingChild child : state.startingChildren()) {
          self.tell(new StartChildCommand(child.agentId(), child.message(), null));
        }
        runner.start(UserMessage.ofText("continue"), state.grants());
        updateSessionStatus(state, SessionStatus.RUNNING);
      }
      case PAUSED -> {
        if (!topology.isRoot()) {
          for (final String interruptId : state.pauseState().pendingExternalSelfInterruptIds()) {
            propagateSelfPauseToParent(topology, interruptId);
          }
        }
        updateSessionStatus(state, SessionStatus.PAUSED);
      }
      case IDLE -> afterComplete(state, true);
    }
  }

  @Override
  public CommandHandler<SessionCommand, SessionFact, SessionActorState> commandHandler() {
    final CommandHandlerBuilder<SessionCommand, SessionFact, SessionActorState> builder =
        newCommandHandlerBuilder();
    builder
        .forAnyState()
        .onCommand(InitializeCommand.class, this::initialize)
        .onCommand(StartCommand.class, this::start)
        .onCommand(ResumeCommand.class, this::resume)
        .onCommand(ResumeChildCommand.class, this::resumeChild)
        .onCommand(CompleteChildCommand.class, this::completeChild)
        .onCommand(ContinueRunCommand.class, this::continueRun)
        .onCommand(AwaitCommand.class, this::await)
        .onCommand(AwaitChildCommand.class, this::awaitChild)
        .onCommand(StartChildCommand.class, this::startChild)
        .onCommand(SendMessageCommand.class, this::sendMessage)
        .onCommand(PublishEventCommand.class, this::publishEvent)
        .onCommand(PauseChildCommand.class, this::childPaused)
        .onCommand(StartChildCompletedCommand.class, this::startChildCompleted)
        .onCommand(CompleteRunCommand.class, this::completeRun)
        .onCommand(StartNextQueuedMessageCommand.class, this::startNextQueuedMessage)
        .onCommand(GetCurrentTurnEventsCommand.class, this::getCurrentTurnEvents)
        .onCommand(RollbackCommand.class, this::rollback)
        .onCommand(SelfPauseCommand.class, this::retryPropagateSelfPause)
        .onCommand(ReapChildCommand.class, this::reapChild)
        .onCommand(ReapChildResultCommand.class, this::reapResult);

    return builder.build();
  }

  private Effect<SessionFact, SessionActorState> initialize(
      final SessionActorState state, final InitializeCommand command) {
    final SessionTopology topology = command.topology();
    // Persist InitializedFact only on first initialization — re-sending InitializeCommand for an
    // existing session (e.g. a new turn on a root session) must not wipe accumulated state.
    if (state.topology() != null) {
      return Effect().none().thenReply(command.replyTo(), _ -> Done.done());
    }
    return Effect()
        .persist(new InitializedFact(topology))
        .thenRun(_ -> init(topology))
        .thenReply(command.replyTo(), _ -> Done.done());
  }

  private void init(final SessionTopology topology) {
    if (runner != null) {
      return;
    }
    final String sessionId = topology.sessionId();
    final AgentSession session = sessionService.getSession(sessionId);
    if (session == null) {
      final ConcurrentMap<String, Object> initialState = SessionUtils.buildInitialState();

      final AgentSession agentSession =
          new AgentSession(sessionId, topology.agentId(), initialState);
      final String parentSessionId = topology.parentSessionId();
      final AgentSession parentSession =
          StringUtils.isNotBlank(parentSessionId)
              ? sessionService.getSession(parentSessionId)
              : null;
      agentSession.setRootSessionId(
          parentSession == null ? sessionId : resolveRootSessionId(parentSession));
      agentSession.setParentSessionId(parentSessionId);
      agentSession.setRootAgentId(
          parentSession == null ? topology.agentId() : resolveRootAgentId(parentSession));
      agentSession.setSpawnedByAgentId(parentSession == null ? null : parentSession.getAgentId());
      agentSession.setDepth(resolveDepth(parentSession));
      agentSession.setStatus(SessionStatus.INIT);
      sessionService.create(agentSession);
    }
    runner = runnerFactory.buildRunner(topology.agentId(), sessionId, self);
  }

  private Effect<SessionFact, SessionActorState> start(
      final SessionActorState state, final StartCommand command) {
    final SessionTopology topology = state.topology();
    final UniqueRecord<UserMessage> message = command.message();
    final UniqueRecord<UserMessage> currentMessage = state.currentMessage();
    boolean isDuplicate =
        Objects.equals(currentMessage, message) || state.queue().contains(message);
    return switch (state.sessionState()) {
      case IDLE -> {
        if (isDuplicate) {
          // Idle with the message still queued means the client retried after a lost reply; the
          // message was already processed, so just resume draining the queue.
          self.tell(new StartNextQueuedMessageCommand());
          yield Effect()
              .none()
              .thenReply(command.replyTo(), _ -> new StartSessionResult.DuplicateRequest());
        } else {
          yield Effect()
              .persist(new MessageEnqueuedFact(message))
              .thenRun(_ -> self.tell(new StartNextQueuedMessageCommand()))
              .thenReply(command.replyTo(), _ -> new StartSessionResult.Accepted());
        }
      }
      default -> {
        if (isDuplicate) {
          yield Effect()
              .none()
              .thenReply(command.replyTo(), _ -> new StartSessionResult.DuplicateRequest());
        }
        if (topology.isRoot()) {
          yield Effect()
              .persist(new MessageEnqueuedFact(message))
              .thenReply(
                  command.replyTo(),
                  newState -> new StartSessionResult.Queued(newState.queue().size()));
        }
        yield Effect()
            .none()
            .thenReply(
                command.replyTo(),
                _ ->
                    new StartSessionResult.Rejected(
                        "Cannot start the child session yet. Await on the session for it to produce result first"));
      }
    };
  }

  private Effect<SessionFact, SessionActorState> resume(
      final SessionActorState state, final ResumeCommand command) {
    final ResumeRequest resumeRequest = command.resumeRequest();
    final String childSessionId = state.getPausedChild(resumeRequest);

    final ActorRef<ResumeResult> replyTo = command.replyTo();
    if (childSessionId != null) {
      final Optional<ChildSession> child = state.child(childSessionId);
      if (child.isEmpty()) {
        return Effect()
            .none()
            .thenReply(
                replyTo,
                _ -> new ResumeResult.Rejected("Unknown child session: " + childSessionId));
      }

      final EntityRef<SessionCommand> childRef = refSupplier.apply(childSessionId);
      context.pipeToSelf(
          childRef.ask(
              (Function<ActorRef<ResumeResult>, SessionCommand>)
                  askReplyTo -> new ResumeCommand(resumeRequest, askReplyTo),
              ASK_TIMEOUT),
          // Piped back as a command rather than handled inline: the future completes on a thread
          // other than the actor's, which cannot use actor-only abstractions like persist().
          (resumeResult, error) ->
              new ResumeChildCommand(
                  resumeRequest, replyTo, resumeResult, error == null ? null : error.getMessage()));
      return Effect().none();
    }

    // Covers both a pending and an already-answered interrupt id; the latter is silently ignored
    // below rather than treated as a failure.
    if (!state.isExternalSelfInterrupt(resumeRequest)) {
      return Effect().none().thenReply(replyTo, _ -> new ResumeResult.UnknownInterruptId());
    }

    return resumed(
        replyTo,
        resumeRequest,
        state
            .pauseState()
            .pendingExternalSelfInterruptIds()
            .contains(resumeRequest.getInterruptId()));
  }

  private Effect<SessionFact, SessionActorState> resumeChild(
      final SessionActorState state, final ResumeChildCommand command) {
    final ActorRef<ResumeResult> replyTo = command.replyTo();
    if (command.error() != null) {
      return Effect()
          .none()
          .thenReply(
              replyTo,
              _ ->
                  new ResumeResult.Rejected(
                      "Failed to forward resume to child session: " + command.error()));
    }

    if (command.result() instanceof ResumeResult.Rejected rejected) {
      return Effect().none().thenReply(replyTo, _ -> rejected);
    }

    return resumed(
        replyTo,
        command.resumeRequest(),
        state
            .pauseState()
            .pendingExternalSelfInterruptIds()
            .contains(command.resumeRequest().getInterruptId()));
  }

  private Effect<SessionFact, SessionActorState> completeChild(
      final SessionActorState state, final CompleteChildCommand command) {
    final String interruptId = state.getInternalInterruptId(command.childSessionId());
    LOG.debug(
        "Received Child completed for topology : {}, child sessionId : {}, interruptId : {}",
        JsonUtils.toJson(state.topology()),
        command.childSessionId(),
        interruptId);
    if (interruptId == null) {
      return Effect().none();
    }
    final String childAgentId = SessionUtils.agentIdFromSessionId(command.childSessionId());
    final String author = childAgentId != null ? childAgentId : Constants.AUTHOR_USER;
    final ResumeRequest resumeRequest =
        new ResumeRequest(
            interruptId,
            true,
            AwaitAgentTool.buildCompletedResponseMap(command.childSessionId(), command.result()),
            author);
    return resumed(null, resumeRequest, true);
  }

  /**
   * {@code pendingSelfInterrupt} must be whether the interrupt was still pending, not just known —
   * otherwise re-resuming an already-answered interrupt would continue the run a second time.
   */
  private Effect<SessionFact, SessionActorState> resumed(
      final ActorRef<ResumeResult> replyTo,
      final ResumeRequest resumeRequest,
      final boolean pendingSelfInterrupt) {
    final EffectBuilder<SessionFact, SessionActorState> builder =
        Effect()
            .persist(new ResumedFact(resumeRequest))
            .thenRun(
                newState -> {
                  LOG.debug(
                      "resumed for topology : {}, all interruptsAnswered : {}, pendingSelfInterrupt:{}",
                      JsonUtils.toJson(newState.topology()),
                      newState.allInterruptsAnswered(),
                      pendingSelfInterrupt);
                  if (pendingSelfInterrupt && newState.allInterruptsAnswered()) {
                    self.tell(new ContinueRunCommand());
                  }
                });
    return replyTo != null ? builder.thenReply(replyTo, _ -> new ResumeResult.Accepted()) : builder;
  }

  private EffectBuilder<SessionFact, SessionActorState> continueRun(
      final SessionActorState state, final ContinueRunCommand command) {
    if (state.sessionState() != SessionState.PAUSED) {
      LOG.debug(
          "Ignoring ContinueRunCommand for session in state {} for topology : {}",
          state.sessionState(),
          JsonUtils.toJson(state.topology()));
      return Effect().none();
    }
    final List<ResumeRequest> resumeRequests =
        state.getAllReceivedResumes().stream()
            .filter(resumeRequest -> !resumedInterruptIds.contains(resumeRequest.getInterruptId()))
            .toList();
    LOG.debug(
        "Continuing run with resumes : {} for topology : {}",
        JsonUtils.toJson(resumeRequests),
        JsonUtils.toJson(state.topology()));
    return Effect()
        .persist(new ContinuingFact())
        .thenRun(
            newState -> {
              resumeRequests.forEach(
                  resumeRequest -> resumedInterruptIds.add(resumeRequest.getInterruptId()));
              runner.resume(resumeRequests, newState.grants());
              LOG.debug(
                  "Continued run with resumes : {} for topology : {}",
                  JsonUtils.toJson(resumeRequests),
                  JsonUtils.toJson(state.topology()));
              updateSessionStatus(newState, SessionStatus.RUNNING);
            });
  }

  private Effect<SessionFact, SessionActorState> await(
      final SessionActorState state, final AwaitCommand command) {
    return switch (state.sessionState()) {
      case PAUSED, TRIGGERED_RUN, RUNNING ->
          Effect().none().thenReply(command.replyTo(), _ -> RunResult.incomplete());
      default -> Effect().none().thenReply(command.replyTo(), _ -> state.lastResult());
    };
  }

  private Effect<SessionFact, SessionActorState> awaitChild(
      final SessionActorState state, final AwaitChildCommand command) {
    final String childSessionId = command.childSessionId();
    final Optional<ChildSession> child = state.child(childSessionId);
    if (child.isEmpty()) {
      return Effect()
          .none()
          .thenReply(
              command.replyTo(),
              _ -> RunResult.failure("Unknown child session: " + childSessionId));
    }
    self.tell(new ReapChildCommand(command.replyTo(), childSessionId, 1));
    return Effect().none();
  }

  private Effect<SessionFact, SessionActorState> reapChild(
      final SessionActorState state, final ReapChildCommand command) {
    final EntityRef<SessionCommand> childRef = refSupplier.apply(command.childSessionId());
    context.pipeToSelf(
        childRef.ask(
            (Function<ActorRef<RunResult>, SessionCommand>) AwaitCommand::new, ASK_TIMEOUT),
        (result, error) ->
            new ReapChildResultCommand(
                command.replyTo(), command.childSessionId(), command.attempt(), result, error));
    return Effect().none();
  }

  private Effect<SessionFact, SessionActorState> reapResult(
      final SessionActorState state, final ReapChildResultCommand command) {
    if (command.error() != null) {
      if (command.attempt() >= MAX_CHILD_POLL_ATTEMPTS) {
        LOG.error(
            "Giving up polling child session {} after {} attempts",
            command.childSessionId(),
            command.attempt());
        command
            .replyTo()
            .tell(
                RunResult.failure(
                    "Child session "
                        + command.childSessionId()
                        + " unreachable after "
                        + command.attempt()
                        + " attempts"));
      } else {
        context
            .getSystem()
            .classicSystem()
            .scheduler()
            .scheduleOnce(
                Duration.ofSeconds(1),
                () ->
                    self.tell(
                        new ReapChildCommand(
                            command.replyTo(), command.childSessionId(), command.attempt() + 1)),
                context.getExecutionContext());
      }
    } else {
      command.replyTo().tell(command.result());
    }
    return Effect().none();
  }

  private Effect<SessionFact, SessionActorState> startChild(
      final SessionActorState state, final StartChildCommand command) {
    final String childAgentId = command.agentId();
    final UniqueRecord<UserMessage> commandMessage = command.message();
    final String childSessionId = commandMessage.getId();
    final UserMessage message = commandMessage.getRecord();
    final ActorRef<StartChildResult> replyTo = command.replyTo();
    if (state.child(childSessionId).isPresent()) {
      final StartChildResult result =
          new StartChildResult(childSessionId, new StartSessionResult.Accepted());
      return replyTo != null ? Effect().none().thenReply(replyTo, _ -> result) : Effect().none();
    }
    final SessionState sessionState = state.sessionState();
    if (sessionState == SessionState.RUNNING
        || ((sessionState == SessionState.TRIGGERED_RUN || sessionState == SessionState.CONTINUING)
            && !turnEvents.isEmpty())) {
      // TRIGGERED_RUN/CONTINUING with events already buffered means the run is actively producing
      // its first turn — sessionState only flips to RUNNING once that turn commits.
      return Effect()
          .persist(
              new ChildStartingFact(
                  new StartingChild(command.agentId(), childSessionId, commandMessage)))
          .thenRun(
              _ ->
                  startChildSession(state, childAgentId, childSessionId, message)
                      .whenComplete(
                          (result, error) -> {
                            self.tell(
                                new StartChildCompletedCommand(
                                    childSessionId,
                                    childAgentId,
                                    replyTo,
                                    result,
                                    error == null ? null : ExceptionUtils.getErrorMessage(error)));
                          }));
    }
    final StartChildResult rejected =
        new StartChildResult(
            null,
            new StartSessionResult.Rejected(
                "The current session is in "
                    + sessionState
                    + " state and cannot spawn a new child"));
    return replyTo != null ? Effect().none().thenReply(replyTo, _ -> rejected) : Effect().none();
  }

  private CompletionStage<StartSessionResult> startChildSession(
      final SessionActorState state,
      final String childAgentId,
      final String childSessionId,
      final UserMessage message) {
    final EntityRef<SessionCommand> childRef = refSupplier.apply(childSessionId);
    final SessionTopology topology = state.topology();
    final SessionTopology childTopology =
        SessionTopology.child(
            childAgentId,
            childSessionId,
            topology.rootSessionId(),
            topology.sessionId(),
            topology.agentId());
    return childRef
        .ask(
            (Function<ActorRef<Done>, SessionCommand>)
                initReplyTo -> new InitializeCommand(childTopology, initReplyTo),
            ASK_TIMEOUT)
        .thenCompose(
            _ -> {
              final UniqueRecord<UserMessage> uniqueMessage = new UniqueRecord<>(message);
              return childRef.ask(
                  (Function<ActorRef<StartSessionResult>, SessionCommand>)
                      startReplyTo -> new StartCommand(uniqueMessage, startReplyTo),
                  ASK_TIMEOUT);
            });
  }

  private Effect<SessionFact, SessionActorState> startChildCompleted(
      final SessionActorState state, final StartChildCompletedCommand command) {
    final ActorRef<StartChildResult> replyTo = command.replyTo();

    if (command.error() != null) {
      final StartChildResult result =
          new StartChildResult(
              command.sessionId(),
              new StartSessionResult.Rejected("Failed to start child session: " + command.error()));
      final var effect = Effect().persist(new ChildStartFailedFact(command.sessionId()));
      return replyTo != null ? effect.thenReply(replyTo, _ -> result) : effect;
    }

    if (command.result() instanceof StartSessionResult.Rejected rejected) {
      final StartChildResult result = new StartChildResult(command.sessionId(), rejected);
      final var effect = Effect().persist(new ChildStartFailedFact(command.sessionId()));
      return replyTo != null ? effect.thenReply(replyTo, _ -> result) : effect;
    }

    final EffectBuilder<SessionFact, SessionActorState> effect =
        Effect().persist(new ChildStartedFact(command.sessionId(), command.agentId()));
    return replyTo != null
        ? effect.thenReply(
            replyTo, _ -> new StartChildResult(command.sessionId(), command.result()))
        : effect;
  }

  private Effect<SessionFact, SessionActorState> sendMessage(
      final SessionActorState state, final SendMessageCommand command) {
    final String childSessionId = command.sessionId();
    final Optional<ChildSession> child = state.child(childSessionId);
    if (child.isEmpty()) {
      return Effect()
          .none()
          .thenReply(
              command.replyTo(),
              _ -> new StartSessionResult.Rejected("Unknown child session: " + childSessionId));
    }

    final EntityRef<SessionCommand> childRef = refSupplier.apply(childSessionId);
    return Effect()
        .none()
        .thenRun(
            _ ->
                childRef
                    .ask(
                        (Function<ActorRef<StartSessionResult>, SessionCommand>)
                            replyTo ->
                                new StartCommand(
                                    new UniqueRecord<>(command.message().getRecord()), replyTo),
                        ASK_TIMEOUT)
                    .whenComplete(
                        (result, error) -> {
                          if (error != null) {
                            command
                                .replyTo()
                                .tell(
                                    new StartSessionResult.Rejected(
                                        "Failed to send message to child session: "
                                            + error.getMessage()));
                            return;
                          }
                          if (result instanceof StartSessionResult.DuplicateRequest) {
                            command.replyTo().tell(new StartSessionResult.Accepted());
                          } else {
                            command.replyTo().tell(result);
                          }
                        }));
  }

  private Effect<SessionFact, SessionActorState> publishEvent(
      final SessionActorState state, final PublishEventCommand command) {
    final Event event = command.event();

    LOG.debug(
        "[USER_MESSAGE_TRACE][{}] SessionActor.publishEvent() received event: author={} turnComplete={} content={}",
        state.topology().sessionId(),
        event.author(),
        event.turnComplete().orElse(false),
        event.toJson());

    // Detect self-pause: the adk_request_confirmation call ID is the interruptId the client
    // echoes back, so it is used directly as the pause key.
    final List<SessionFact> pauseFacts = new ArrayList<>();
    final List<String> externalInterruptIds = new ArrayList<>();
    final List<FunctionCall> interruptCalls = Functions.getAskUserConfirmationFunctionCalls(event);
    for (final FunctionCall call : interruptCalls) {
      final String interruptId = call.id().orElse(null);
      final Map<String, Object> args = call.args().orElse(Map.of());
      final FunctionCall originalFunctionCall =
          Objects.requireNonNull(getValueFromMap(args, ORIGINAL_FUNCTION_CALL));

      final String functionName = originalFunctionCall.name().orElse(null);
      if (Constants.ToolNames.isAgentRoutingTool(functionName)) {
        final ToolConfirmation toolConfirmation =
            getValueFromMap(args, Constants.ToolArgs.TOOL_CONFIRMATION);
        if (toolConfirmation != null) {
          //noinspection unchecked
          final String childSessionId =
              getValueFromMap(
                  (Map<String, Object>) toolConfirmation.payload(),
                  AbstractAgentTool.CHILD_SESSION_ID);
          pauseFacts.add(PausedFact.internalSelfPause(childSessionId, interruptId));
        }
      } else {
        pauseFacts.add(PausedFact.externalSelfPaused(interruptId));
        externalInterruptIds.add(interruptId);
      }
    }

    LOG.debug("Publishing event : {}", JsonUtils.toJson(event));
    if (turnEvents.isEmpty()) {
      turnId = UUID.randomUUID().toString();
    }
    turnEvents.add(event);
    LOG.debug(
        "[USER_MESSAGE_TRACE][{}] Added event to turnEvents queue. Queue size now: {}",
        state.topology().sessionId(),
        turnEvents.size());
    final long eventSequence = state.nextSequence() + turnEvents.size() - 1;

    final SessionTopology topology = state.topology();
    final String rootSessionId = topology.rootSessionId();
    final SessionEvent toPublish =
        SessionEventUtils.toSessionEvent(
            rootSessionId,
            topology.parentSessionId(),
            topology.sessionId(),
            turnId,
            event,
            eventSequence);
    EffectBuilder<SessionFact, SessionActorState> effectBuilder;
    if (!event.turnComplete().orElse(false)) {
      LOG.debug(
          "[USER_MESSAGE_TRACE][{}] Turn NOT complete, pauseFacts.isEmpty()={}",
          topology.sessionId(),
          CollectionUtils.isEmpty(pauseFacts));
      if (CollectionUtils.isEmpty(pauseFacts)) {
        LOG.debug("Publishing without any effect");
        effectBuilder = Effect().none();
      } else {
        LOG.debug("Publishing with pause effect");
        effectBuilder =
            Effect()
                .persist(pauseFacts)
                .thenRun(
                    newState -> {
                      externalInterruptIds.forEach(
                          id -> propagateSelfPauseToParent(newState.topology(), id));
                      updateSessionStatus(newState, SessionStatus.PAUSED);
                    });
      }
    } else {
      LOG.debug(
          "[USER_MESSAGE_TRACE][{}] Turn COMPLETE! Committing {} events to TurnCommittedFact",
          topology.sessionId(),
          turnEvents.size());
      LOG.debug("committing on turn completion : {}", JsonUtils.toJson(turnEvents));

      if (state.isDuplicateTurn(turnEvents.getLast())) {
        LOG.warn("Duplicate turn detected for session {}, skipping commit", topology.sessionId());
        effectBuilder = Effect().none();
        turnEvents.clear();
        turnId = null;
      } else {
        final ArrayList<Event> events = new ArrayList<>();
        final boolean isFirstTurn = state.isFirstTurnOfCurrentRun();
        final RunState currentRun = Objects.requireNonNull(state.currentRun());
        final String invocationId = currentRun.runId();
        final long timestamp = currentRun.messageTimestamp();

        if (isFirstTurn && currentRun.message() != null) {
          final UniqueRecord<UserMessage> userMessage = currentRun.message();
          final String author =
              topology.isRoot() ? Constants.AUTHOR_USER : topology.parentAgentId();
          events.add(
              EventUtils.buildUserEvent(userMessage.getRecord(), invocationId, timestamp, author));
          LOG.debug(
              "[USER_MESSAGE_TRACE][{}] Run's opening turn - prepended user message event: '{}' with invocationId: {}",
              topology.sessionId(),
              userMessage.getRecord(),
              invocationId);
        }

        if (!state.getAllReceivedResumes().isEmpty()) {
          final String author =
              state.getAllReceivedResumes().stream()
                  .findFirst()
                  .map(ResumeRequest::getAuthor)
                  .orElse(topology.isRoot() ? Constants.AUTHOR_USER : topology.parentAgentId());

          events.add(
              EventUtils.buildResumeEvent(state.getAllReceivedResumes(), invocationId, author));
          LOG.debug(
              "[USER_MESSAGE_TRACE][{}] Turn after resume - prepended {} resume answer(s) with invocationId: {}",
              topology.sessionId(),
              state.getAllReceivedResumes().size(),
              invocationId);
        }

        events.addAll(turnEvents);

        final TurnCommittedFact turnFact =
            commitTurn(
                SessionEventUtils.toSessionEvents(
                    rootSessionId,
                    topology.parentSessionId(),
                    topology.sessionId(),
                    turnId,
                    events,
                    eventSequence),
                invocationId);

        turnEvents.clear();
        turnId = null;
        resumedInterruptIds.clear();
        LOG.debug(
            "[USER_MESSAGE_TRACE][{}] Creating TurnCommittedFact with {} events. Event details:",
            topology.sessionId(),
            events.size());
        for (int i = 0; i < events.size(); i++) {
          final Event evt = events.get(i);
          LOG.debug(
              "[USER_MESSAGE_TRACE][{}]   Event #{}: author={}, content={}",
              topology.sessionId(),
              i,
              evt.author(),
              evt.content().map(Content::text).orElse("<no-content>"));
        }
        final List<SessionFact> commitFacts = new ArrayList<>();
        commitFacts.add(turnFact);
        commitFacts.addAll(pauseFacts);
        LOG.debug("Publishing with commit effect");
        effectBuilder =
            Effect()
                .persist(commitFacts)
                .thenRun(
                    newState -> {
                      externalInterruptIds.forEach(
                          id -> propagateSelfPauseToParent(newState.topology(), id));
                      if (newState.sessionState() == SessionState.PAUSED) {
                        updateSessionStatus(newState, SessionStatus.PAUSED);
                      }
                    });
      }
    }
    return effectBuilder.thenRun(
        _ -> {
          LOG.debug(
              "Publishing adk event : {} as session event :{}",
              JsonUtils.toJson(event),
              JsonUtils.toJson(toPublish));
          eventChannel.publish(rootSessionId, toPublish);
        });
  }

  private Effect<SessionFact, SessionActorState> childPaused(
      final SessionActorState state, final PauseChildCommand command) {
    EffectBuilder<SessionFact, SessionActorState> effect;
    if (state.pauseState().getPausedChild(command.interruptId()) != null) {
      effect = Effect().none();
    } else {
      effect =
          Effect().persist(PausedFact.childPaused(command.childSessionId(), command.interruptId()));
    }
    return effect.thenReply(command.replyTo(), _ -> Done.done());
  }

  /**
   * TODO: retry is bounded ask attempts plus an in-memory rescheduled fallback ({@link
   * #SELF_PAUSE_RETRY_INTERVAL}); neither survives this actor's process dying while a retry is
   * armed. Recovery re-arms it on restart, but nothing else proactively wakes a paused child, so if
   * the process dies mid-retry and this actor is never touched again, propagation is lost for good.
   * Closing that fully requires an external reconciliation sweep over PAUSED sessions with
   * unresolved {@code pendingExternalSelfInterruptIds} — out of scope here; risk accepted as low
   * (needs message loss and no further restart/relocation of this actor for the pause's lifetime).
   */
  private void propagateSelfPauseToParent(
      final SessionTopology topology, final String interruptId) {
    if (topology.isRoot()) {
      return;
    }

    final String currentSessionId = topology.sessionId();
    final String parentSessionId = topology.parentSessionId();
    final String parentAgentId = topology.parentAgentId();
    final EntityRef<SessionCommand> parent = refSupplier.apply(parentSessionId);

    // Captured on the actor's own thread; whenComplete below may run on another thread, and
    // these are the only two actor-affiliated references that are safe to touch from there.
    final Scheduler scheduler = context.getSystem().classicSystem().scheduler();
    final ExecutionContextExecutor executionContext = context.getExecutionContext();

    Patterns.retry(
            () ->
                parent.ask(
                    (Function<ActorRef<Done>, SessionCommand>)
                        replyTo -> new PauseChildCommand(currentSessionId, interruptId, replyTo),
                    ASK_TIMEOUT),
            5,
            Duration.ofSeconds(1),
            Duration.ofSeconds(30),
            0.2,
            context.getSystem())
        .whenComplete(
            (ignored, error) -> {
              if (error != null) {
                LOG.error(
                    "Failed to propagate paused interrupt '{}' from session '{}' to parent '{}:{}' "
                        + "after retries; scheduling async retry in {}",
                    interruptId,
                    currentSessionId,
                    parentAgentId,
                    parentSessionId,
                    SELF_PAUSE_RETRY_INTERVAL,
                    error);
                scheduler.scheduleOnce(
                    SELF_PAUSE_RETRY_INTERVAL,
                    () -> self.tell(new SelfPauseCommand(topology, interruptId)),
                    executionContext);
              }
            });
  }

  /**
   * Fallback for when the bounded ask-retry in {@link #propagateSelfPauseToParent} is exhausted.
   * Keeps rescheduling itself until the interrupt is no longer pending — either the parent finally
   * durably persisted the routing entry, or the human answered it directly, making propagation
   * moot. This also self-heals across actor restarts: {@code onRecoveryCompleted} already
   * re-invokes {@code propagateSelfPauseToParent} for every still-pending interrupt, which re-arms
   * this loop even if the in-memory schedule was lost.
   */
  private Effect<SessionFact, SessionActorState> retryPropagateSelfPause(
      final SessionActorState state, final SelfPauseCommand command) {
    if (state.pauseState().pendingExternalSelfInterruptIds().contains(command.interruptId())) {
      propagateSelfPauseToParent(command.topology(), command.interruptId());
    }
    return Effect().none();
  }

  private Effect<SessionFact, SessionActorState> getCurrentTurnEvents(
      final SessionActorState state, final GetCurrentTurnEventsCommand command) {
    return Effect()
        .none()
        .thenReply(
            command.replyTo(),
            newState -> {
              final SessionTopology topology = newState.topology();
              final List<SessionEvent> events =
                  SessionEventUtils.toSessionEvents(
                      topology.rootSessionId(),
                      topology.parentSessionId(),
                      topology.sessionId(),
                      turnId,
                      new ArrayList<>(turnEvents),
                      newState.nextSequence());
              return new CurrentTurnEvents(events);
            });
  }

  private Effect<SessionFact, SessionActorState> rollback(
      final SessionActorState state, final RollbackCommand command) {
    if (state.sessionState() == SessionState.RUNNING) {
      return Effect()
          .none()
          .thenReply(command.replyTo(), _ -> new RollbackResult.Rejected("A run is in progress"));
    }
    final Long rollbackSequence = state.findRunStartSequence(command.runId());
    if (rollbackSequence == null) {
      // No run with this id ever started, so there is nothing to invalidate or reset back to.
      return Effect().none().thenReply(command.replyTo(), _ -> new RollbackResult.Applied());
    }
    // RollbackFact persists first: it's the source of truth, so a crash before it lands means the
    // rollback simply never happened, and the caller's ask times out with nothing changed.
    return Effect()
        .persist(new RollbackFact(command.runId()))
        .thenRun(
            newState -> invalidateRolledBackEvents(newState, command.runId(), rollbackSequence))
        .thenReply(command.replyTo(), _ -> new RollbackResult.Applied());
  }

  private void invalidateRolledBackEvents(
      final SessionActorState state, final String runId, final long rollbackSequence) {
    sessionEventsRepository.updateMany(
        Filters.and(
            Filters.eq(SessionEvent.FIELD_SESSION_ID, state.topology().sessionId()),
            Filters.gte(SessionEvent.FIELD_SEQUENCE, rollbackSequence)),
        Update.of(Operation.set(SessionEvent.FIELD_ROLLBACK_ID, runId)));
  }

  private Effect<SessionFact, SessionActorState> startNextQueuedMessage(
      final SessionActorState state, final StartNextQueuedMessageCommand command) {
    if (state.sessionState() != SessionState.IDLE || state.queue().isEmpty()) {
      return Effect().none();
    }

    final UniqueRecord<UserMessage> nextMessage = state.queue().peek();
    return Effect()
        .persist(new StartedFact(nextMessage))
        .thenRun(
            newState -> {
              LOG.debug(
                  "[USER_MESSAGE_TRACE][{}] Starting message: '{}'",
                  newState.topology().sessionId(),
                  nextMessage.getRecord());
              updateSessionStatus(newState, SessionStatus.RUNNING);
              runner.start(nextMessage.getRecord(), newState.grants());
            });
  }

  @Override
  public EventHandler<SessionActorState, SessionFact> eventHandler() {
    return newEventHandlerBuilder()
        .forAnyState()
        .onEvent(
            InitializedFact.class,
            (_, fact) -> SessionActorState.initial().withTopology(fact.getTopology()))
        .onEvent(
            StartedFact.class,
            (state, fact) ->
                state
                    .dequeue()
                    .withSessionState(SessionState.TRIGGERED_RUN)
                    .withNewRun(fact.getMessage(), fact.getTimestamp().toEpochMilli()))
        .onEvent(
            ResumedFact.class,
            (state, fact) -> {
              final ResumeRequest resumeRequest = fact.getResumeRequest();
              LOG.debug(
                  "received resumed fact : {} for topology : {} for resume request : {}",
                  state.isSelfInterrupt(resumeRequest),
                  JsonUtils.toJson(state.topology()),
                  JsonUtils.toJson(resumeRequest));
              return state.isSelfInterrupt(resumeRequest)
                  ? state.selfResume(resumeRequest)
                  : state.childResume(resumeRequest);
            })
        .onEvent(
            ContinuingFact.class, (state, _) -> state.withSessionState(SessionState.CONTINUING))
        .onEvent(
            PausedFact.class,
            (state, fact) -> {
              LOG.debug(
                  "paused fact for topology : {}, {}",
                  JsonUtils.toJson(state.topology()),
                  JsonUtils.toJson(fact));
              final String childSessionId = fact.getSessionId();
              final String interruptId = fact.getInterruptId();
              if (childSessionId != null) {
                return state.childPaused(childSessionId, interruptId);
              }
              if (fact.isInternal()) {
                String correlationId = fact.getCorrelationId();
                correlationId = correlationId == null ? interruptId : correlationId;
                return state.withInternalSelfPause(correlationId, interruptId);
              }
              return state.selfPaused(interruptId);
            })
        .onEvent(TurnCommittedFact.class, SessionActor::applyCommittedTurn)
        .onEvent(RollbackFact.class, (state, fact) -> state.withRollback(fact.getRunId()))
        .onEvent(
            CompletedFact.class,
            (state, fact) -> {
              final RunResult result =
                  fact.getError() != null
                      ? RunResult.failure(fact.getError())
                      : RunResult.success(fact.getFinalAnswer());
              return state.completeRun(result);
            })
        .onEvent(MessageEnqueuedFact.class, (state, fact) -> state.enqueue(fact.getMessage()))
        .onEvent(ChildStartingFact.class, (state, fact) -> state.startingChild(fact.getChild()))
        .onEvent(
            ChildStartFailedFact.class,
            (state, fact) -> state.childStartFailed(fact.getSessionId()))
        .onEvent(
            ChildStartedFact.class,
            (state, fact) ->
                state.startedChild(fact.getSessionId(), new ChildSession(fact.getAgentId(), null)))
        .build();
  }

  /**
   * Inserts before returning the fact the caller persists, deliberately: {@link TurnCommittedFact}
   * carries only turn metadata, not the events themselves, so persisting it first and losing this
   * write to a crash would make that turn's content unrecoverable — worse than the current
   * ordering, where a crash between the two just orphans rows that {@code onRecoveryCompleted}
   * sweeps up. The residual gap is a read landing in between: {@code
   * getCommittedSessionEvents}/{@code findTurnEvents} only filter on {@code rollbackId}, so a
   * caller querying in that window sees these rows before the fact confirms them — data that is
   * correct if nothing crashes, and quietly disappears on the next recovery if something does.
   * Accepted as a narrow, momentary risk rather than adding a pending/confirmed marker and
   * filtering every reader by it.
   */
  private TurnCommittedFact commitTurn(final List<SessionEvent> events, final String runId) {
    sessionEventsRepository.insertMany(SessionEventUtils.compactEventStream(events));
    final String lastEventId = events.isEmpty() ? null : events.getLast().getId();
    return new TurnCommittedFact(runId, turnId, lastEventId, events.size());
  }

  private static SessionActorState applyCommittedTurn(
      final SessionActorState state, final TurnCommittedFact fact) {
    SessionActorState newState =
        state.withCommittedTurn(fact.getTurnId(), fact.getEventCount(), fact.getLastEventId());
    final SessionState existingState = state.sessionState();
    if (existingState == SessionState.TRIGGERED_RUN || existingState == SessionState.CONTINUING) {
      newState = newState.withSessionState(SessionState.RUNNING);
    }

    if (state.allInterruptsAnswered()) {
      LOG.debug(
          "Applying committed turn for topology : {} and clearing interrupt state : {}",
          JsonUtils.toJson(newState.topology()),
          JsonUtils.toJson(newState.getAllReceivedResumes()));
      newState = newState.clearSelfInterruptStates();
    }
    return newState;
  }

  private Effect<SessionFact, SessionActorState> completeRun(
      final SessionActorState state, final CompleteRunCommand command) {
    final SessionState sessionState = state.sessionState();
    if (sessionState != SessionState.RUNNING
        && sessionState != SessionState.TRIGGERED_RUN
        && sessionState != SessionState.CONTINUING) {
      return Effect().none();
    }
    final String error = command.error();
    final List<SessionFact> facts = new ArrayList<>();
    final boolean isFailed = StringUtils.isNotEmpty(error);
    if (isFailed) {
      LOG.warn("Run failed for session {}: {}", state.topology().sessionId(), error);
      final SessionTopology topology = state.topology();
      final List<SessionEvent> sessionEvents = new ArrayList<>();
      if (CollectionUtils.isNotEmpty(turnEvents)) {
        sessionEvents.addAll(
            SessionEventUtils.toSessionEvents(
                topology.rootSessionId(),
                topology.parentSessionId(),
                topology.sessionId(),
                turnId,
                turnEvents,
                state.nextSequence()));
      }

      final SessionEvent errorEvent =
          SessionEvent.error(
              topology.rootSessionId(),
              topology.sessionId(),
              error,
              state.nextSequence() + sessionEvents.size(),
              turnId);
      sessionEvents.add(errorEvent);
      facts.add(commitTurn(sessionEvents, Objects.requireNonNull(state.currentRun()).runId()));
      turnEvents.clear();
      turnId = null;
    }
    facts.add(
        new CompletedFact(isFailed ? null : extractFinalAnswer(state), isFailed ? error : null));
    return Effect().persist(facts).thenRun(newState -> afterComplete(newState, false));
  }

  private void afterComplete(final SessionActorState state, final boolean isRecovery) {
    final SessionTopology topology = state.topology();
    final String sessionId = topology.sessionId();
    final String rootSessionId = topology.rootSessionId();
    final RunResult runResult = state.lastResult();
    final boolean isFailed = runResult != null && runResult.isFailure();
    updateSessionStatus(state, isFailed ? SessionStatus.FAILED : SessionStatus.COMPLETED);
    if (isFailed) {
      eventChannel.publish(
          rootSessionId,
          SessionEvent.error(
              rootSessionId,
              sessionId,
              runResult.failureMessage(),
              Long.MAX_VALUE - 1,
              state.lastCommittedTurn().turnId()));
    }
    if (topology.isRoot()) {
      generateSessionTitle(rootSessionId, isRecovery);
      updateSessionMemory(rootSessionId, isRecovery);
      eventChannel.publish(rootSessionId, SessionEvent.terminal(sessionId));
      if (!state.queue().isEmpty()) {
        self.tell(new StartNextQueuedMessageCommand());
      }
    } else {
      LOG.debug("Child completed for topology : {}", JsonUtils.toJson(topology));
      refSupplier
          .apply(topology.parentSessionId())
          .tell(new CompleteChildCommand(topology.sessionId(), runResult));
    }
  }

  /**
   * Extracts and persists memories from the completed session asynchronously, so it does not block
   * the actor's message-processing loop. Memory extraction involves an LLM call and multiple
   * vector-store writes. Skipped on recovery — same reasoning as {@link
   * #generateSessionTitle(String, boolean)}.
   */
  private void updateSessionMemory(final String rootSessionId, final boolean isRecovery) {
    if (isRecovery) {
      return;
    }
    ASYNC_TASK_EXECUTOR.execute(
        () -> {
          try {
            final AgentSession session = sessionService.getSession(rootSessionId);
            if (session == null) {
              return;
            }
            final Session adkSession =
                Session.builder(rootSessionId)
                    .appName(session.getAgentId())
                    .userId(AgentSession.DEFAULT_USER_ID)
                    .build();
            memoryService.addSessionToMemory(adkSession).blockingAwait();
          } catch (final Exception e) {
            LOG.warn("Failed to update session memory for session {}", rootSessionId, e);
          }
        });
  }

  /**
   * Generates and persists a session title asynchronously so it does not block the actor's
   * message-processing loop. Title generation involves an LLM call followed by a MongoDB write,
   * both of which are unsuitable for the actor thread. Skipped on recovery: an actor only recovers
   * into a completed session via {@code IDLE} state, which is only reached after the session's one
   * genuine completion already ran this.
   */
  private void generateSessionTitle(final String rootSessionId, final boolean isRecovery) {
    if (isRecovery) {
      return;
    }
    ASYNC_TASK_EXECUTOR.execute(
        () -> {
          try {
            final String title = sessionTitleGenerator.generateTitle(rootSessionId);
            if (StringUtils.isNotBlank(title)) {
              sessionService.updateSession(
                  rootSessionId, Update.of(Operation.set(AgentSession.FIELD_NAME, title)));
            }
          } catch (final Exception e) {
            LOG.warn("Failed to generate session title for session {}", rootSessionId, e);
          }
        });
  }

  private String extractFinalAnswer(final SessionActorState state) {
    final CommittedTurn lastCommittedTurn = state.lastCommittedTurn();
    if (lastCommittedTurn == null) {
      return null;
    }
    final List<Event> events =
        sessionEventsRepository.findTurnEvents(
            state.topology().sessionId(), lastCommittedTurn.turnId());
    for (int i = events.size() - 1; i >= 0; i--) {
      final Optional<Content> content = events.get(i).content();
      if (content.isPresent()) {
        final String text = content.get().text();
        if (StringUtils.isNotBlank(text)) {
          return text;
        }
      }
    }
    return null;
  }

  private void updateSessionStatus(final SessionActorState state, final SessionStatus status) {
    final SessionTopology topology = state.topology();
    try {
      final Query query =
          new Query()
              .withFilter(
                  Filters.and(
                      Filters.eq(BaseEntity.FIELD_ID, topology.sessionId()),
                      Filters.ne(AgentSession.FIELD_STATUS, status.name())));
      sessionService.updateSessions(
          query, Update.of(Operation.set(AgentSession.FIELD_STATUS, status.name())));
    } catch (final Exception e) {
      LOG.warn(
          "Failed to update session status to {} for session {}", status, topology.sessionId(), e);
    }
  }

  private static String resolveRootSessionId(final AgentSession parentSession) {
    if (parentSession == null || StringUtils.isBlank(parentSession.getRootSessionId())) {
      return parentSession == null ? null : parentSession.getId();
    }
    return parentSession.getRootSessionId();
  }

  private static String resolveRootAgentId(final AgentSession parentSession) {
    if (parentSession == null || StringUtils.isBlank(parentSession.getRootAgentId())) {
      return parentSession == null ? null : parentSession.getAgentId();
    }
    return parentSession.getRootAgentId();
  }

  private static int resolveDepth(final AgentSession parent) {
    return parent == null ? 1 : parent.getDepth() + 1;
  }
}
