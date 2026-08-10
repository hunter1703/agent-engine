package com.agentengine.agent.core.session;

import static com.agentengine.agent.core.session.SessionActorFactory.ASK_TIMEOUT;
import static com.agentengine.util.agents.Constants.ARG_ORIGINAL_FUNCTION_CALL;

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
import com.agentengine.agent.core.tools.agent.AwaitAgentTool;
import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.beans.session.SessionStatus;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.ExceptionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.pekko.actor.ShardedEntity;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentMap;
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

/**
 * Persistent, cluster-sharded actor that manages a single agent session.
 */
public final class SessionActor extends ShardedEntity<SessionCommand, SessionFact, SessionActorState> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionActor.class);

    public static final EntityTypeKey<SessionCommand> TYPE_KEY =
            EntityTypeKey.create(SessionCommand.class, AssetClass.AGENT_SESSION);

    private static final int MAX_CHILD_POLL_ATTEMPTS = 10;
    private static final Duration SELF_PAUSE_RETRY_INTERVAL = Duration.ofMinutes(1);

    private final ActorContext<SessionCommand> context;
    private final ActorRef<SessionCommand> self;
    private final int snapshotThreshold;
    private final SessionEventChannel eventChannel;
    private final Queue<Event> turnEvents = new ArrayDeque<>();
    private final java.util.function.Function<String, EntityRef<SessionCommand>> refSupplier;
    private final RunnerFactory runnerFactory;
    private final SessionService sessionService;
    private final SessionTitleGenerator sessionTitleGenerator;
    private final MemoryService memoryService;
    private SessionRunner runner;
    private boolean runStarted = false;

    public SessionActor(
            final ActorContext<SessionCommand> context,
            final String entityId,
            final int snapshotThreshold,
            final SessionEventChannel eventChannel,
            final java.util.function.Function<String, EntityRef<SessionCommand>> refSupplier,
            final RunnerFactory runnerFactory,
            final SessionService sessionService,
            final SessionTitleGenerator sessionTitleGenerator,
            final MemoryService memoryService) {
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
    }

    @Override
    public SessionActorState emptyState() {
        return SessionActorState.initial();
    }

    @Override
    public SignalHandler<SessionActorState> signalHandler() {
        return newSignalHandlerBuilder()
                .onSignal(RecoveryCompleted.class, (state, _) -> onRecoveryCompleted(state))
                .onSignal(PostStop.class, (_, _) -> {
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
     * <p>Per-event methods ({@code enqueue}, {@code dequeue}, etc.) mutate shared backing
     * collections for O(1) replay. {@link SessionActorState#copy()} produces fresh
     * collection instances so the persisted snapshot is never affected by later mutations.
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
        final String rootSessionId = topology.rootSessionId();
        switch (sessionState) {
            case TRIGGERED_RUN -> {
                // re-start with message; as the recovery state was mid first turn
                runner.start(state.runState().message().getRecord());
                updateSessionStatus(state, SessionStatus.RUNNING);
            }
            case RESUMING -> {
                runner.resume(state.getAllReceivedConfirmations());
                updateSessionStatus(state, SessionStatus.RUNNING);
            }
            case RUNNING -> {
                runStarted = true;
                final List<Event> lastCommitedEvents =
                        CollectionUtils.nullSafeList(state.runState().lastCommittedTurn());
                final int num = lastCommitedEvents.size();

                for (int i = 0; i < num; i++) {
                    final SessionEvent sessionEvent = SessionEventUtils.toSessionEvent(
                            rootSessionId,
                            topology.parentSessionId(),
                            sessionId,
                            lastCommitedEvents.get(i),
                            state.nextSequence() - num + i);
                    eventChannel.publish(rootSessionId, sessionEvent);
                }

                for (final StartingChild child : state.startingChildren()) {
                    self.tell(new StartChildCommand(child.agentId(), child.message(), null));
                }
                runner.start(UserMessage.ofText("continue"));
                updateSessionStatus(state, SessionStatus.RUNNING);
            }
            case PAUSED -> {
                if (!topology.isRoot()) {
                    for (final String confirmationId : state.pauseState().pendingExternalSelfConfirmationIds()) {
                        propagateSelfPauseToParent(topology, confirmationId);
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
        builder.forAnyState()
                .onCommand(InitializeCommand.class, this::initialize)
                .onCommand(StartCommand.class, this::start)
                .onCommand(ConfirmCommand.class, this::confirm)
                .onCommand(ConfirmChildCommand.class, this::confirmChild)
                .onCommand(CompleteChildCommand.class, this::completeChild)
                .onCommand(ResumeCommand.class, (state, _) -> {
                    // Only resume if the session is actually paused;
                    if (state.sessionState() != SessionState.PAUSED) {
                        LOG.info(
                                "Ignoring ResumeCommand for session in state {} for topology : {}",
                                state.sessionState(),
                                JsonUtils.toJson(state.topology()));
                        return Effect().none();
                    }
                    final Collection<Confirmation> confirmations = state.getAllReceivedConfirmations();
                    LOG.info(
                            "Resuming confirmations : {} for topology : {}",
                            JsonUtils.toJson(confirmations),
                            JsonUtils.toJson(state.topology()));
                    return Effect().persist(new ResumingFact()).thenRun(newState -> {
                        runner.resume(confirmations);
                        LOG.info(
                                "Resumed confirmations : {} for topology : {}",
                                JsonUtils.toJson(confirmations),
                                JsonUtils.toJson(state.topology()));
                        updateSessionStatus(newState, SessionStatus.RUNNING);
                    });
                })
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
                .onCommand(SelfPauseCommand.class, this::retryPropagateSelfPause);

        return builder.build();
    }

    private Effect<SessionFact, SessionActorState> initialize(
            final SessionActorState state, final InitializeCommand command) {
        final SessionTopology topology = command.topology();
        // Only persist InitializedFact on the first initialization. Re-sending
        // InitializeCommand for an existing session (e.g. a new turn on a root session)
        // must not reset the actor state — doing so wipes any other accumulated state.
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

            final AgentSession agentSession = new AgentSession(sessionId, topology.agentId(), initialState);
            final String parentSessionId = topology.parentSessionId();
            final AgentSession parentSession =
                    StringUtils.isNotBlank(parentSessionId) ? sessionService.getSession(parentSessionId) : null;
            agentSession.setRootSessionId(parentSession == null ? sessionId : resolveRootSessionId(parentSession));
            agentSession.setParentSessionId(parentSessionId);
            agentSession.setRootAgentId(parentSession == null ? topology.agentId() : resolveRootAgentId(parentSession));
            agentSession.setSpawnedByAgentId(parentSession == null ? null : parentSession.getAgentId());
            agentSession.setDepth(resolveDepth(parentSession));
            agentSession.setStatus(SessionStatus.INIT);
            sessionService.create(agentSession);
        }
        runner = runnerFactory.buildRunner(topology.agentId(), sessionId, self);
    }

    private Effect<SessionFact, SessionActorState> start(final SessionActorState state, final StartCommand command) {
        final SessionTopology topology = state.topology();
        final UniqueRecord<UserMessage> message = command.message();
        final UniqueRecord<UserMessage> currentMessage = state.runState().message();
        boolean isDuplicate =
                Objects.equals(currentMessage, message) || state.queue().contains(message);
        return switch (state.sessionState()) {
            case IDLE -> {
                if (isDuplicate) {
                    // if message present in queue but still in idle state, something is wrong and trigger to the
                    // message
                    // processing loop
                    self.tell(new StartNextQueuedMessageCommand());
                    yield Effect().none().thenReply(command.replyTo(), _ -> new StartSessionResult.DuplicateRequest());
                } else {
                    yield Effect()
                            .persist(new MessageEnqueuedFact(message))
                            .thenRun(_ -> self.tell(new StartNextQueuedMessageCommand()))
                            .thenReply(command.replyTo(), _ -> new StartSessionResult.Accepted());
                }
            }
            default -> {
                if (isDuplicate) {
                    yield Effect().none().thenReply(command.replyTo(), _ -> new StartSessionResult.DuplicateRequest());
                }
                if (topology.isRoot()) {
                    yield Effect()
                            .persist(new MessageEnqueuedFact(message))
                            .thenReply(
                                    command.replyTo(),
                                    newState -> new StartSessionResult.Queued(
                                            newState.queue().size()));
                }
                yield Effect()
                        .none()
                        .thenReply(
                                command.replyTo(),
                                _ -> new StartSessionResult.Rejected(
                                        "Cannot start the child session yet. Await on the session for it to produce result first"));
            }
        };
    }

    private Effect<SessionFact, SessionActorState> confirm(
            final SessionActorState state, final ConfirmCommand command) {
        final Confirmation confirmation = command.confirmation();
        final String childSessionId = state.getPausedChild(confirmation);

        final ActorRef<ConfirmResult> replyTo = command.replyTo();
        if (childSessionId != null) {
            final Optional<ChildSession> child = state.child(childSessionId);
            if (child.isEmpty()) {
                return Effect()
                        .none()
                        .thenReply(
                                replyTo, _ -> new ConfirmResult.Rejected("Unknown child session: " + childSessionId));
            }

            final EntityRef<SessionCommand> childRef = refSupplier.apply(childSessionId);
            context.pipeToSelf(
                    childRef.ask(
                            (Function<ActorRef<ConfirmResult>, SessionCommand>)
                                    askReplyTo -> new ConfirmCommand(confirmation, askReplyTo),
                            ASK_TIMEOUT),
                    // processing result via command and not inline because completable future thread would be different
                    // then actor thread so cannot access actor-specific abstractions like "persist", etc.
                    (resumeResult, error) -> new ConfirmChildCommand(
                            confirmation, replyTo, resumeResult, error == null ? null : error.getMessage()));
            return Effect().none();
        }

        // checking whether it is either accepted or pending confirmation id. if already accepted id is received, it is
        // not a failure but we will silently ignore in event handler
        if (!state.isExternalSelfConfirmation(confirmation)) {
            return Effect().none().thenReply(replyTo, _ -> new ConfirmResult.UnknownConfirmationId());
        }

        return confirmed(
                replyTo,
                confirmation,
                // only checking whether PENDING confirmation id was received; if we did not have this check, everytime
                // someone sends already accepted confirmation id it would resume the agent
                state.pauseState().pendingExternalSelfConfirmationIds().contains(confirmation.getConfirmationId()));
    }

    private Effect<SessionFact, SessionActorState> confirmChild(
            final SessionActorState state, final ConfirmChildCommand command) {
        final ActorRef<ConfirmResult> replyTo = command.replyTo();
        if (command.error() != null) {
            return Effect()
                    .none()
                    .thenReply(
                            replyTo,
                            _ -> new ConfirmResult.Rejected(
                                    "Failed to forward resume to child session: " + command.error()));
        }

        if (command.result() instanceof ConfirmResult.Rejected rejected) {
            return Effect().none().thenReply(replyTo, _ -> rejected);
        }

        return confirmed(
                replyTo,
                command.confirmation(),
                // only checking whether PENDING confirmation id was received; if we did not have this check, everytime
                // someone sends already accepted confirmation id it would resume the agent
                state.pauseState()
                        .pendingExternalSelfConfirmationIds()
                        .contains(command.confirmation().getConfirmationId()));
    }

    private Effect<SessionFact, SessionActorState> completeChild(
            final SessionActorState state, final CompleteChildCommand command) {
        final String confirmationId = state.getInternalConfirmationId(command.childSessionId());
        LOG.info(
                "Received Child completed for topology : {}, child sessionId : {}, confirmationId : {}",
                JsonUtils.toJson(state.topology()),
                command.childSessionId(),
                confirmationId);
        if (confirmationId == null) {
            return Effect().none();
        }
        final Confirmation confirmation = new Confirmation(
                confirmationId,
                true,
                AwaitAgentTool.buildCompletedResponseMap(command.childSessionId(), command.result()));
        return confirmed(null, confirmation, true);
    }

    private Effect<SessionFact, SessionActorState> confirmed(
            final ActorRef<ConfirmResult> replyTo,
            final Confirmation confirmation,
            final boolean pendingSelfConfirmation) {
        final EffectBuilder<SessionFact, SessionActorState> builder = Effect()
                .persist(new ConfirmedFact(confirmation))
                .thenRun(newState -> {
                    LOG.info(
                            "confirmed for topology : {}, all confirmationsReceived : {}, pendingSelfConfirmation:{}",
                            JsonUtils.toJson(newState.topology()),
                            newState.allConfirmationsReceived(),
                            pendingSelfConfirmation);
                    if (pendingSelfConfirmation && newState.allConfirmationsReceived()) {
                        self.tell(new ResumeCommand());
                    }
                });
        return replyTo != null ? builder.thenReply(replyTo, _ -> new ConfirmResult.Accepted()) : builder;
    }

    private Effect<SessionFact, SessionActorState> await(final SessionActorState state, final AwaitCommand command) {
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
                    .thenReply(command.replyTo(), _ -> RunResult.failure("Unknown child session: " + childSessionId));
        }
        pollChildForResult(command.replyTo(), childSessionId);
        return Effect().none();
    }

    private void pollChildForResult(final ActorRef<RunResult> replyToRef, final String childSessionId) {
        pollChildForResult(replyToRef, childSessionId, 1);
    }

    private void pollChildForResult(
            final ActorRef<RunResult> replyToRef, final String childSessionId, final int attempt) {
        final EntityRef<SessionCommand> childRef = refSupplier.apply(childSessionId);
        childRef.ask((Function<ActorRef<RunResult>, SessionCommand>) AwaitCommand::new, ASK_TIMEOUT)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        if (attempt >= MAX_CHILD_POLL_ATTEMPTS) {
                            LOG.error("Giving up polling child session {} after {} attempts", childSessionId, attempt);
                            replyToRef.tell(RunResult.failure(
                                    "Child session " + childSessionId + " unreachable after " + attempt + " attempts"));
                        } else {
                            // retry on timeout — child may still be starting up
                            pollChildForResult(replyToRef, childSessionId, attempt + 1);
                        }
                    } else {
                        replyToRef.tell(result);
                    }
                });
    }

    private Effect<SessionFact, SessionActorState> startChild(
            final SessionActorState state, final StartChildCommand command) {
        final String childAgentId = command.agentId();
        final UniqueRecord<String> commandMessage = command.message();
        final String childSessionId = commandMessage.getId();
        final String message = commandMessage.getRecord();
        final ActorRef<StartChildResult> replyTo = command.replyTo();
        if (state.child(childSessionId).isPresent()) {
            // a child has already started; it is a duplicate request
            final StartChildResult result = new StartChildResult(childSessionId, new StartSessionResult.Accepted());
            return replyTo != null
                    ? Effect().none().thenReply(replyTo, _ -> result)
                    : Effect().none();
        }
        final SessionState sessionState = state.sessionState();
        if (sessionState == SessionState.RUNNING
                || ((sessionState == SessionState.TRIGGERED_RUN || sessionState == SessionState.RESUMING)
                        && runStarted)) {
            // (sessionState == SessionState.TRIGGERED_RUN || sessionState == SessionState.RESUMING) && runStarted when
            // the spawn child is invoked in first run or first run when the state is still in TRIGGERED_RUN after
            // resume ; state moves to RUNNING only when the first turn is committed
            return Effect()
                    .persist(
                            new ChildStartingFact(new StartingChild(command.agentId(), childSessionId, commandMessage)))
                    .thenRun(_ -> startChildSession(state, childAgentId, childSessionId, message)
                            .whenComplete((result, error) -> {
                                self.tell(new StartChildCompletedCommand(
                                        childSessionId,
                                        childAgentId,
                                        replyTo,
                                        result,
                                        error == null ? null : ExceptionUtils.getErrorMessage(error)));
                            }));
        }
        final StartChildResult rejected = new StartChildResult(
                null,
                new StartSessionResult.Rejected(
                        "The current session is in " + sessionState + " state and cannot spawn a new child"));
        return replyTo != null
                ? Effect().none().thenReply(replyTo, _ -> rejected)
                : Effect().none();
    }

    private CompletionStage<StartSessionResult> startChildSession(
            final SessionActorState state,
            final String childAgentId,
            final String childSessionId,
            final String message) {
        final EntityRef<SessionCommand> childRef = refSupplier.apply(childSessionId);
        final SessionTopology topology = state.topology();
        final SessionTopology childTopology = SessionTopology.child(
                childAgentId, childSessionId, topology.rootSessionId(), topology.sessionId(), topology.agentId());
        return childRef.ask(
                        (Function<ActorRef<Done>, SessionCommand>)
                                initReplyTo -> new InitializeCommand(childTopology, initReplyTo),
                        ASK_TIMEOUT)
                .thenCompose(_ -> {
                    final UniqueRecord<UserMessage> uniqueMessage = new UniqueRecord<>(UserMessage.ofText(message));
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
            final StartChildResult result = new StartChildResult(
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

        final var effect = Effect().persist(new ChildStartedFact(command.sessionId(), command.agentId()));
        return replyTo != null
                ? effect.thenReply(replyTo, _ -> new StartChildResult(command.sessionId(), command.result()))
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
                .thenRun(_ -> childRef.ask(
                                (Function<ActorRef<StartSessionResult>, SessionCommand>) replyTo -> new StartCommand(
                                        new UniqueRecord<>(UserMessage.ofText(
                                                command.message().getRecord())),
                                        replyTo),
                                ASK_TIMEOUT)
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                command.replyTo()
                                        .tell(new StartSessionResult.Rejected(
                                                "Failed to send message to child session: " + error.getMessage()));
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

        LOG.info(
                "[USER_MESSAGE_TRACE][{}] SessionActor.publishEvent() received event: author={} turnComplete={} content={}",
                state.topology().sessionId(),
                event.author(),
                event.turnComplete().orElse(false),
                event.content().map(Content::text).orElse("<no-content>"));

        // Detect self-pause: the adk_request_confirmation call ID is the confirmationId the client
        // echoes back, so it is used directly as the pause key.
        final List<SessionFact> pauseFacts = new ArrayList<>();
        final List<String> externalConfirmationIds = new ArrayList<>();
        final List<FunctionCall> confirmationCalls = Functions.getAskUserConfirmationFunctionCalls(event);
        for (final FunctionCall call : confirmationCalls) {
            final String confirmationId = call.id().orElse(null);
            final Map<String, Object> args = call.args().orElse(Map.of());
            final FunctionCall originalFunctionCall =
                    Objects.requireNonNull(CollectionUtils.getValueFromMap(args, ARG_ORIGINAL_FUNCTION_CALL));
            if (Objects.equals(
                    Constants.AWAIT_AGENT_TOOL_NAME, originalFunctionCall.name().orElse(null))) {
                final String childSessionId = CollectionUtils.getValueFromMap(
                        originalFunctionCall.args().orElse(Map.of()), AwaitAgentTool.CHILD_SESSION_ID);
                pauseFacts.add(PausedFact.internalSelfPause(childSessionId, confirmationId));
            } else {
                pauseFacts.add(PausedFact.externalSelfPaused(confirmationId));
                externalConfirmationIds.add(confirmationId);
            }
        }

        LOG.info("Publishing event : {}", JsonUtils.toJson(event));
        runStarted = true;
        turnEvents.add(event);
        LOG.info(
                "[USER_MESSAGE_TRACE][{}] Added event to turnEvents queue. Queue size now: {}",
                state.topology().sessionId(),
                turnEvents.size());
        final long eventSequence = state.nextSequence() + turnEvents.size() - 1;

        final SessionTopology topology = state.topology();
        final String rootSessionId = topology.rootSessionId();
        EffectBuilder<SessionFact, SessionActorState> effectBuilder;
        if (!event.turnComplete().orElse(false)) {
            LOG.info(
                    "[USER_MESSAGE_TRACE][{}] Turn NOT complete, pauseFacts.isEmpty()={}",
                    topology.sessionId(),
                    CollectionUtils.isEmpty(pauseFacts));
            if (CollectionUtils.isEmpty(pauseFacts)) {
                LOG.info("Publishing without any effect");
                effectBuilder = Effect().none();
            } else {
                LOG.info("Publishing with pause effect");
                effectBuilder = Effect().persist(pauseFacts).thenRun(newState -> {
                    externalConfirmationIds.forEach(id -> propagateSelfPauseToParent(newState.topology(), id));
                    updateSessionStatus(newState, SessionStatus.PAUSED);
                });
            }
        } else {
            final ArrayList<Event> events = new ArrayList<>(turnEvents);
            LOG.info(
                    "[USER_MESSAGE_TRACE][{}] Turn COMPLETE! Committing {} events to TurnCommittedFact",
                    topology.sessionId(),
                    events.size());
            LOG.info("committing on turn completion : {}", JsonUtils.toJson(events));
            turnEvents.clear();

            if (state.isDuplicateTurn(events)) {
                LOG.warn("Duplicate turn detected for session {}, skipping commit", topology.sessionId());
                effectBuilder = Effect().none();
            } else {
                // Prepend user message/confirmations on first turn
                final boolean isFirstTurn = state.runState().lastCommittedTurn() == null;
                final String invocationId = events.getFirst().invocationId();
                if (isFirstTurn) {
                    if (state.runState().message() != null) {
                        // Initial user message
                        final UniqueRecord<UserMessage> userMessage =
                                state.runState().message();
                        final Event userEvent = EventUtils.buildUserEvent(
                                userMessage.getRecord(),
                                invocationId,
                                events.getFirst().timestamp());
                        events.addFirst(userEvent);
                        LOG.info(
                                "[USER_MESSAGE_TRACE][{}] First turn - prepended user message event: '{}' with invocationId: {}",
                                topology.sessionId(),
                                userMessage.getRecord(),
                                invocationId);
                    }
                } else if (!state.getAllReceivedConfirmations().isEmpty()) {
                    // Resume with confirmations
                    final Event confirmationEvent = EventUtils.buildConfirmationsEvent(
                            state.getAllReceivedConfirmations(),
                            invocationId,
                            events.getFirst().timestamp());
                    events.addFirst(confirmationEvent);
                    LOG.info(
                            "[USER_MESSAGE_TRACE][{}] First turn after resume - prepended {} confirmation(s) with invocationId: {}",
                            topology.sessionId(),
                            state.getAllReceivedConfirmations().size(),
                            invocationId);
                }

                final TurnCommittedFact turnFact = new TurnCommittedFact(events);
                LOG.info(
                        "[USER_MESSAGE_TRACE][{}] Creating TurnCommittedFact with {} events. Event details:",
                        topology.sessionId(),
                        events.size());
                for (int i = 0; i < events.size(); i++) {
                    final Event evt = events.get(i);
                    LOG.info(
                            "[USER_MESSAGE_TRACE][{}]   Event #{}: author={}, content={}",
                            topology.sessionId(),
                            i,
                            evt.author(),
                            evt.content().map(Content::text).orElse("<no-content>"));
                }
                final List<SessionFact> commitFacts = new ArrayList<>();
                commitFacts.add(turnFact);
                commitFacts.addAll(pauseFacts);
                LOG.info("Publishing with commit effect");
                effectBuilder = Effect().persist(commitFacts).thenRun(newState -> {
                    externalConfirmationIds.forEach(id -> propagateSelfPauseToParent(newState.topology(), id));
                    if (newState.sessionState() == SessionState.PAUSED) {
                        updateSessionStatus(newState, SessionStatus.PAUSED);
                    }
                });
            }
        }
        return effectBuilder.thenRun(_ -> {
            final SessionEvent sessionEvent = SessionEventUtils.toSessionEvent(
                    rootSessionId, topology.parentSessionId(), topology.sessionId(), event, eventSequence);
            LOG.info(
                    "Publishing adk event : {} as session event :{}",
                    JsonUtils.toJson(event),
                    JsonUtils.toJson(sessionEvent));
            eventChannel.publish(rootSessionId, sessionEvent);
        });
    }

    private Effect<SessionFact, SessionActorState> childPaused(
            final SessionActorState state, final PauseChildCommand command) {
        EffectBuilder<SessionFact, SessionActorState> effect;
        if (state.pauseState().getPausedChild(command.confirmationId()) != null) {
            // already received the pause command
            effect = Effect().none();
        } else {
            effect = Effect().persist(PausedFact.childPaused(command.childSessionId(), command.confirmationId()));
        }
        return effect.thenReply(command.replyTo(), _ -> Done.done());
    }

    /**
     * TODO: retry is bounded ask attempts plus an in-memory rescheduled fallback ({@link
     * #SELF_PAUSE_RETRY_INTERVAL}); neither survives this actor's process dying while a retry is
     * armed. Recovery re-arms it on restart, but nothing else proactively wakes a paused child, so
     * if the process dies mid-retry and this actor is never touched again, propagation is lost for
     * good. Closing that fully requires an external reconciliation sweep over PAUSED sessions with
     * unresolved {@code pendingExternalSelfConfirmationIds} — out of scope here; risk accepted as
     * low (needs message loss and no further restart/relocation of this actor for the pause's
     * lifetime).
     */
    private void propagateSelfPauseToParent(final SessionTopology topology, final String confirmationId) {
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
                        () -> parent.ask(
                                (Function<ActorRef<Done>, SessionCommand>)
                                        replyTo -> new PauseChildCommand(currentSessionId, confirmationId, replyTo),
                                ASK_TIMEOUT),
                        5,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(30),
                        0.2,
                        context.getSystem())
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOG.error(
                                "Failed to propagate pause confirmation '{}' from session '{}' to parent '{}:{}' "
                                        + "after retries; scheduling async retry in {}",
                                confirmationId,
                                currentSessionId,
                                parentAgentId,
                                parentSessionId,
                                SELF_PAUSE_RETRY_INTERVAL,
                                error);
                        scheduler.scheduleOnce(
                                SELF_PAUSE_RETRY_INTERVAL,
                                () -> self.tell(new SelfPauseCommand(topology, confirmationId)),
                                executionContext);
                    }
                });
    }

    /**
     * Fallback for when the bounded ask-retry in {@link #propagateSelfPauseToParent} is exhausted.
     * Keeps rescheduling itself until the confirmation is no longer pending — either the parent
     * finally durably persisted the routing entry, or the human answered it directly, making
     * propagation moot. This also self-heals across actor restarts: {@code onRecoveryCompleted}
     * already re-invokes {@code propagateSelfPauseToParent} for every still-pending confirmation,
     * which re-arms this loop even if the in-memory schedule was lost.
     */
    private Effect<SessionFact, SessionActorState> retryPropagateSelfPause(
            final SessionActorState state, final SelfPauseCommand command) {
        if (state.pauseState().pendingExternalSelfConfirmationIds().contains(command.confirmationId())) {
            propagateSelfPauseToParent(command.topology(), command.confirmationId());
        }
        return Effect().none();
    }

    private Effect<SessionFact, SessionActorState> getCurrentTurnEvents(
            final SessionActorState state, final GetCurrentTurnEventsCommand command) {
        return Effect().none().thenReply(command.replyTo(), newState -> {
            final SessionTopology topology = newState.topology();
            final List<SessionEvent> events = SessionEventUtils.toSessionEvents(
                    topology.rootSessionId(),
                    topology.parentSessionId(),
                    topology.sessionId(),
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
        return Effect()
                .persist(new RollbackFact(command.runId()))
                .thenReply(command.replyTo(), _ -> new RollbackResult.Applied());
    }

    private Effect<SessionFact, SessionActorState> startNextQueuedMessage(
            final SessionActorState state, final StartNextQueuedMessageCommand command) {
        if (state.sessionState() != SessionState.IDLE || state.queue().isEmpty()) {
            return Effect().none();
        }

        final UniqueRecord<UserMessage> nextMessage = state.queue().peek();
        return Effect().persist(new StartedFact(nextMessage)).thenRun(newState -> {
            LOG.info(
                    "[USER_MESSAGE_TRACE][{}] Starting message: '{}'",
                    newState.topology().sessionId(),
                    nextMessage.getRecord());
            updateSessionStatus(newState, SessionStatus.RUNNING);
            runner.start(nextMessage.getRecord());
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
                        (state, fact) -> state.dequeue()
                                .withSessionState(SessionState.TRIGGERED_RUN)
                                .withCurrentMessage(fact.getMessage()))
                .onEvent(ConfirmedFact.class, (state, fact) -> {
                    final Confirmation confirmation = fact.getConfirmation();
                    LOG.info(
                            "received confirmed fact : {} for topology : {} for confirmation : {}",
                            state.isSelfConfirmation(confirmation),
                            JsonUtils.toJson(state.topology()),
                            JsonUtils.toJson(confirmation));
                    return state.isSelfConfirmation(confirmation)
                            ? state.selfConfirm(confirmation)
                            : state.childConfirm(confirmation);
                })
                .onEvent(ResumingFact.class, (state, _) -> state.withSessionState(SessionState.RESUMING))
                .onEvent(PausedFact.class, (state, fact) -> {
                    LOG.info(
                            "paused fact for topology : {}, {}",
                            JsonUtils.toJson(state.topology()),
                            JsonUtils.toJson(fact));
                    final String childSessionId = fact.getSessionId();
                    final String confirmationId = fact.getConfirmationId();
                    if (childSessionId != null) {
                        return state.childPaused(childSessionId, confirmationId);
                    }
                    runStarted = false;
                    if (fact.isInternal()) {
                        String correlationId = fact.getCorrelationId();
                        correlationId = correlationId == null ? confirmationId : correlationId;
                        return state.withInternalSelfPause(correlationId, confirmationId);
                    }
                    return state.selfPaused(confirmationId);
                })
                .onEvent(TurnCommittedFact.class, SessionActor::applyCommittedTurn)
                .onEvent(CompletedFact.class, (state, fact) -> {
                    runStarted = false;
                    final RunResult result = fact.getError() != null
                            ? RunResult.failure(fact.getError())
                            : RunResult.success(fact.getFinalAnswer());
                    return state.completeRun(result);
                })
                .onEvent(MessageEnqueuedFact.class, (state, fact) -> state.enqueue(fact.getMessage()))
                .onEvent(ChildStartingFact.class, (state, fact) -> state.startingChild(fact.getChild()))
                .onEvent(ChildStartFailedFact.class, (state, fact) -> state.childStartFailed(fact.getSessionId()))
                .onEvent(
                        ChildStartedFact.class,
                        (state, fact) ->
                                state.startedChild(fact.getSessionId(), new ChildSession(fact.getAgentId(), null)))
                .build();
    }

    private static SessionActorState applyCommittedTurn(final SessionActorState state, final TurnCommittedFact fact) {
        final List<Event> events = CollectionUtils.nullSafeList(fact.getEvents());
        SessionActorState newState = state.withCommitedEvents(events);
        final SessionState existingState = state.sessionState();
        if (existingState == SessionState.TRIGGERED_RUN || existingState == SessionState.RESUMING) {
            newState = newState.withSessionState(SessionState.RUNNING);
        }

        if (state.allConfirmationsReceived()) {
            // clear all states as they have been consumed
            LOG.info(
                    "Applying committed turn for topology : {} and clearing confirmations : {}",
                    JsonUtils.toJson(newState.topology()),
                    JsonUtils.toJson(newState.getAllReceivedConfirmations()));
            newState = newState.clearSelfConfirmationStates();
        }
        return newState;
    }

    private Effect<SessionFact, SessionActorState> completeRun(
            final SessionActorState state, final CompleteRunCommand command) {
        final SessionState sessionState = state.sessionState();
        if (sessionState != SessionState.RUNNING
                && sessionState != SessionState.TRIGGERED_RUN
                && sessionState != SessionState.RESUMING) {
            return Effect().none();
        }
        final String error = command.error();
        final List<SessionFact> facts = new ArrayList<>();
        final boolean isFailed = StringUtils.isNotEmpty(error);
        if (isFailed) {
            LOG.warn("Run failed for session {}: {}", state.topology().sessionId(), error);
            if (CollectionUtils.isNotEmpty(turnEvents)) {
                facts.add(new TurnCommittedFact(List.copyOf(turnEvents)));
                turnEvents.clear();
            }
        }
        facts.add(new CompletedFact(isFailed ? null : extractFinalAnswer(state), isFailed ? error : null));
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
                    SessionEvent.error(rootSessionId, sessionId, runResult.failureMessage(), Long.MAX_VALUE - 1));
        }
        if (topology.isRoot()) {
            generateSessionTitle(rootSessionId, isRecovery);
            updateSessionMemory(rootSessionId);
            eventChannel.publish(rootSessionId, SessionEvent.terminal(sessionId));
            if (!state.queue().isEmpty()) {
                self.tell(new StartNextQueuedMessageCommand());
            }
        } else {
            LOG.info("Child completed for topology : {}", JsonUtils.toJson(topology));
            refSupplier
                    .apply(topology.parentSessionId())
                    .tell(new CompleteChildCommand(topology.sessionId(), runResult));
        }
    }

    /**
     * Extracts and persists memories from the completed session asynchronously, so it does not
     * block the actor's message-processing loop. Memory extraction involves an LLM call and
     * multiple vector-store writes.
     */
    private void updateSessionMemory(final String rootSessionId) {
        Thread.startVirtualThread(() -> {
            try {
                final AgentSession session = sessionService.getSession(rootSessionId);
                if (session == null) {
                    return;
                }
                final Session adkSession = Session.builder(rootSessionId)
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
     * both of which are unsuitable for the actor thread.
     */
    private void generateSessionTitle(final String rootSessionId, final boolean isRecovery) {
        Thread.startVirtualThread(() -> {
            try {
                if (isRecovery) {
                    final AgentSession session = sessionService.getSession(rootSessionId);
                    if (session != null && StringUtils.isNotBlank(session.getName())) {
                        return;
                    }
                }
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

    private static String extractFinalAnswer(final SessionActorState state) {
        final RunState runState = state.runState();
        if (runState == null) {
            return null;
        }
        final List<Event> events = CollectionUtils.nullSafeList(runState.lastCommittedTurn());
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
            sessionService.updateSession(
                    topology.sessionId(),
                    Update.of(
                            Operation.set(AgentSession.FIELD_STATUS, status.name()),
                            Operation.set(BaseEntity.FIELD_UPDATED_TIME, System.currentTimeMillis())));
        } catch (final Exception e) {
            LOG.warn("Failed to update session status to {} for session {}", status, topology.sessionId(), e);
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
