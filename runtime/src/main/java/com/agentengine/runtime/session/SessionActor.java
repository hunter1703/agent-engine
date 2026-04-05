package com.agentengine.runtime.session;

import static com.agentengine.runtime.session.SessionActorFactory.ASK_TIMEOUT;

import com.agentengine.core.api.services.SessionService;
import com.agentengine.runtime.factories.RunnerFactory;
import com.agentengine.runtime.session.commands.ExternalCommand.*;
import com.agentengine.runtime.session.commands.InternalCommand.*;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.session.events.*;
import com.agentengine.runtime.session.state.*;
import com.agentengine.runtime.utils.EventUtils;
import com.agentengine.runtime.utils.SessionUtils;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.beans.session.SessionStatus;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.ExceptionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.pekko.actor.ShardedEntity;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentMap;

import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.SnapshotAdapter;
import org.apache.pekko.persistence.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent, cluster-sharded actor that manages a single agent session.
 */
public final class SessionActor extends ShardedEntity<SessionCommand, SessionFact, SessionActorState> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionActor.class);

    public static final EntityTypeKey<SessionCommand> TYPE_KEY =
            EntityTypeKey.create(SessionCommand.class, AssetClass.AGENT_SESSION);

    private final ActorContext<SessionCommand> context;
    private final ActorRef<SessionCommand> self;
    private final int snapshotThreshold;
    private final SessionEventChannel eventChannel;
    private final Queue<Event> turnEvents = new ArrayDeque<>();
    private final java.util.function.Function<String, EntityRef<SessionCommand>> refSupplier;
    private final RunnerFactory runnerFactory;
    private final SessionService sessionService;
    private SessionRunner runner;

    public SessionActor(
            final ActorContext<SessionCommand> context,
            final String entityId,
            final int snapshotThreshold,
            final SessionEventChannel eventChannel,
            final java.util.function.Function<String, EntityRef<SessionCommand>> refSupplier,
            final RunnerFactory runnerFactory,
            final SessionService sessionService) {
        super(TYPE_KEY.name(), entityId);
        this.context = context;
        this.self = context.getSelf();
        this.snapshotThreshold = snapshotThreshold;
        this.eventChannel = eventChannel;
        this.refSupplier = refSupplier;
        this.runnerFactory = runnerFactory;
        this.sessionService = sessionService;
    }

    @Override
    public SessionActorState emptyState() {
        return SessionActorState.initial();
    }

    @Override
    public SignalHandler<SessionActorState> signalHandler() {
        return newSignalHandlerBuilder()
                .onSignal(RecoveryCompleted.class, (state, signal) -> onRecoveryCompleted(state))
                .onSignal(PostStop.class, (_, _) -> {
                    if (runner != null) {
                        runner.cancel();
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
        if (sessionState == SessionState.TRIGGERED_RUN) {
            final String sessionId = topology.sessionId();
            final AgentSession session = sessionService.getSession(sessionId);
            if (session != null) {
                sessionService.deleteSession(sessionId);
            }
        }
        init(topology);
        switch (sessionState) {
            case TRIGGERED_RUN -> {
                // re-start with message; as the recovery state was mid first turn
                runner.start(state.currentMessage().getRecord());
                updateSessionStatus(state, SessionStatus.RUNNING);
            }
            case RUNNING -> {
                final List<Event> lastCommitedEvents = CollectionUtils.nullSafeList(state.lastCommittedEvents());
                final int num = lastCommitedEvents.size();

                for (int i = 0; i < num; i++) {
                    final SessionEvent sessionEvent = SessionEventUtils.toSessionEvent(topology.rootSessionId(), topology.parentSessionId(), topology.sessionId(), lastCommitedEvents.get(i), state.nextSequence() - num + i);
                    eventChannel.publish(topology.rootSessionId(), sessionEvent);
                }

                for (final StartingChild child : state.startingChildren()) {
                    self.tell(new StartChildCommand(child.agentId(), child.message(), null));
                }
                final Collection<Confirmation> confirmations = state.getAllReceivedConfirmations();
                if (CollectionUtils.isNotEmpty(confirmations)) {
                    // confirmations already received — resume directly rather than starting a fresh run
                    self.tell(new ResumeCommand());
                } else {
                    runner.start("continue");
                }
                updateSessionStatus(state, SessionStatus.RUNNING);
            }
            case PAUSED -> {
                if (!topology.isRoot()) {
                    for (final String confirmationId : state.pauseState().pendingSelfConfirmationIds()) {
                        propagateSelfPauseToParent(topology, confirmationId);
                    }
                }
                updateSessionStatus(state, SessionStatus.PAUSED);
            }
            case IDLE -> {
                updateSessionStatus(
                        state,
                        state.lastResult() != null && state.lastResult().isFailure()
                                ? SessionStatus.FAILED
                                : SessionStatus.COMPLETED);
                if (!state.queue().isEmpty()) {
                    self.tell(new StartNextQueuedMessageCommand());
                }
            }
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
                .onCommand(ResumeCommand.class, (state, _) -> {
                    final Collection<Confirmation> confirmations = state.getAllReceivedConfirmations();
                    return Effect().persist(new ResumedFact()).thenRun(newState -> {
                        runner.resume(confirmations);
                        updateSessionStatus(newState, SessionStatus.RUNNING);
                    });
                })
                .onCommand(AwaitCommand.class, this::await)
                .onCommand(StartChildCommand.class, this::startChild)
                .onCommand(SendMessageCommand.class, this::sendMessage)
                .onCommand(PublishEventCommand.class, this::publishEvent)
                .onCommand(ChildPausedCommand.class, this::childPaused)
                .onCommand(StartChildCompletedCommand.class, this::startChildCompleted)
                .onCommand(RunFailedCommand.class, this::runFailed)
                .onCommand(StartNextQueuedMessageCommand.class, this::startNextQueuedMessage);

        return builder.build();
    }

    private Effect<SessionFact, SessionActorState> initialize(
            final SessionActorState state, final InitializeCommand command) {
        final SessionTopology topology = command.topology();
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
        final UniqueRecord<String> message = command.message();
        final UniqueRecord<String> currentMessage = state.currentMessage();
        boolean isDuplicate =
                Objects.equals(currentMessage, message) || state.queue().contains(message);
        return switch (state.sessionState()) {
            case IDLE -> {
                if (isDuplicate) {
                    // if message present in queue but still in idle state something is wrong and trigger to the message
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
            case RUNNING, TRIGGERED_RUN, PAUSED -> {
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
                    // then actor thread so cannot access actor specific abstractions like persist, etc.
                    (resumeResult, error) -> new ConfirmChildCommand(
                            confirmation, replyTo, resumeResult, error == null ? null : error.getMessage()));
            return Effect().none();
        }

        if (!state.isSelfConfirmation(confirmation)) {
            return Effect().none().thenReply(replyTo, _ -> new ConfirmResult.UnknownConfirmationId());
        }

        return confirmed(
                replyTo,
                confirmation,
                state.pauseState().pendingSelfConfirmationIds().contains(confirmation.getConfirmationId()));
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
                state.pauseState()
                        .pendingSelfConfirmationIds()
                        .contains(command.confirmation().getConfirmationId()));
    }

    private ReplyEffect<SessionFact, SessionActorState> confirmed(
            final ActorRef<ConfirmResult> replyTo,
            final Confirmation confirmation,
            final boolean pendingSelfConfirmation) {
        return Effect()
                .persist(new ConfirmedFact(confirmation))
                .thenRun(newState -> {
                    if (pendingSelfConfirmation && newState.allConfirmationsReceived()) {
                        self.tell(new ResumeCommand());
                    }
                })
                .thenReply(replyTo, _ -> new ConfirmResult.Accepted());
    }

    private Effect<SessionFact, SessionActorState> await(final SessionActorState state, final AwaitCommand command) {
        final String childSessionId = command.childSessionId();
        if (childSessionId == null) {
            // await is on current session
            return switch (state.sessionState()) {
                case PAUSED, TRIGGERED_RUN, RUNNING ->
                        Effect().none().thenReply(command.replyTo(), _ -> RunResult.incomplete());
                default -> Effect().none().thenReply(command.replyTo(), _ -> state.lastResult());
            };
        }

        final Optional<ChildSession> child = state.child(childSessionId);
        if (child.isEmpty()) {
            return Effect()
                    .none()
                    .thenReply(command.replyTo(), _ -> RunResult.failure("Unknown child session: " + childSessionId));
        }

        final EntityRef<SessionCommand> childRef = refSupplier.apply(childSessionId);
        childRef.ask(
                        (Function<ActorRef<RunResult>, SessionCommand>) replyTo -> new AwaitCommand(null, replyTo),
                        ASK_TIMEOUT)
                .whenComplete((result, error) -> command.replyTo()
                        .tell(
                                error == null
                                        ? result
                                        : RunResult.failure("Failed to await child session: " + error.getMessage())));
        return Effect().none();
    }

    private Effect<SessionFact, SessionActorState> startChild(
            final SessionActorState state, final StartChildCommand command) {
        final String childAgentId = command.agentId();
        final UniqueRecord<String> commandMessage = command.message();
        final String childSessionId = commandMessage.getId();
        final String message = commandMessage.getRecord();
        if (state.child(childSessionId).isPresent()) {
            // a child has already started; it is a duplicate request
            return Effect()
                    .none()
                    .thenReply(
                            command.replyTo(),
                            _ -> new StartChildResult(childSessionId, new StartSessionResult.Accepted()));
        }
        return switch (state.sessionState()) {
            case RUNNING -> Effect()
                    .persist(new ChildStartingFact(
                            new StartingChild(command.agentId(), childSessionId, commandMessage)))
                    .thenRun(_ -> {
                        startChildSession(state, childAgentId, childSessionId, message)
                                .whenComplete((result, error) -> {
                                    self.tell(new StartChildCompletedCommand(
                                            childSessionId,
                                            childAgentId,
                                            command.replyTo(),
                                            result,
                                            error == null ? null : ExceptionUtils.getErrorMessage(error)));
                                });
                    });
            default -> Effect()
                    .none()
                    .thenReply(
                            command.replyTo(),
                            _ -> new StartChildResult(
                                    null,
                                    new StartSessionResult.Rejected("The current session is in "
                                            + state.sessionState()
                                            + " state and cannot spawn a new child")));
        };
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
                    final UniqueRecord<String> uniqueMessage = new UniqueRecord<>(message);
                    // TODO: add retry to send same unique message if some transient error
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
            return replyTo != null
                    ? Effect().none().thenReply(replyTo, _ -> result)
                    : Effect().none();
        }

        if (command.result() instanceof StartSessionResult.Rejected rejected) {
            final StartChildResult result = new StartChildResult(command.sessionId(), rejected);
            return replyTo != null
                    ? Effect().none().thenReply(replyTo, _ -> result)
                    : Effect().none();
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
                                (Function<ActorRef<StartSessionResult>, SessionCommand>)
                                        replyTo -> new StartCommand(command.message(), replyTo),
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

    private Effect<SessionFact, SessionActorState> publishEvent(final SessionActorState state, final PublishEventCommand command) {
        final Event event = command.event();

        // Detect self-pause: the adk_request_confirmation call ID is the confirmationId the client
        // echoes back, so it is used directly as the pause key.
        final List<SessionFact> pauseFacts = new ArrayList<>();
        final List<String> newPauseIds = new ArrayList<>();
        final List<FunctionCall> confirmationCalls = Functions.getAskUserConfirmationFunctionCalls(event);
        for (final FunctionCall call : confirmationCalls) {
            final String confirmationId = call.id().orElse(null);
            if (confirmationId != null) {
                pauseFacts.add(PausedFact.selfPaused(confirmationId));
                newPauseIds.add(confirmationId);
            }
        }

        turnEvents.add(event);

        EffectBuilder<SessionFact, SessionActorState> effectBuilder;
        if (!event.turnComplete().orElse(false)) {
            if (CollectionUtils.isEmpty(pauseFacts)) {
                effectBuilder = Effect().none();
            } else {
                effectBuilder = Effect().persist(pauseFacts).thenRun(newState -> {
                    newPauseIds.forEach(id -> propagateSelfPauseToParent(newState.topology(), id));
                    updateSessionStatus(newState, SessionStatus.PAUSED);
                });
            }
        } else {
            final ArrayList<Event> events = new ArrayList<>(turnEvents);
            turnEvents.clear();

            if (state.isDuplicateTurn(events)) {
                effectBuilder = Effect().none();
            } else {
                final TurnCommittedFact turnFact = new TurnCommittedFact(
                        events,
                        null,
                        EventUtils.isTerminal(event)
                                ? event.content().orElse(Content.builder().build()).text()
                                : null);
                final List<SessionFact> commitFacts = new ArrayList<>(pauseFacts);
                commitFacts.add(turnFact);
                effectBuilder = Effect().persist(commitFacts).thenRun(newState -> {
                    newPauseIds.forEach(id -> propagateSelfPauseToParent(newState.topology(), id));
                    if (newState.sessionState() == SessionState.IDLE) {
                        updateSessionStatus(
                                newState,
                                newState.lastResult() != null && newState.lastResult().isFailure()
                                        ? SessionStatus.FAILED
                                        : SessionStatus.COMPLETED);
                        if (newState.topology().isRoot() && !newState.queue().isEmpty()) {
                            self.tell(new StartNextQueuedMessageCommand());
                        }
                    } else if (newState.sessionState() == SessionState.PAUSED) {
                        updateSessionStatus(newState, SessionStatus.PAUSED);
                    }
                });
            }
        }
        final SessionTopology topology = state.topology();
        final String rootSessionId = topology.rootSessionId();
        return effectBuilder.thenRun(_ -> eventChannel.publish(rootSessionId, SessionEventUtils.toSessionEvent(rootSessionId, topology.parentSessionId(), topology.sessionId(), event, state.nextSequence() + turnEvents.size())));
    }

    private Effect<SessionFact, SessionActorState> childPaused(
            final SessionActorState state, final ChildPausedCommand command) {
        return switch (state.sessionState()) {
            case TRIGGERED_RUN, PAUSED, RUNNING -> Effect()
                    .persist(PausedFact.childPaused(command.childSessionId(), command.confirmationId()))
                    .thenRun(newState -> updateSessionStatus(newState, SessionStatus.PAUSED))
                    .thenReply(command.replyTo(), _ -> Done.done());
            default -> Effect().none().thenReply(command.replyTo(), _ -> Done.done());
        };
    }

    private void propagateSelfPauseToParent(final SessionTopology topology, final String confirmationId) {
        if (topology.isRoot()) {
            return;
        }

        final String currentSessionId = topology.sessionId();
        final String parentSessionId = topology.parentSessionId();
        final String parentAgentId = topology.parentAgentId();
        final EntityRef<SessionCommand> parent = refSupplier.apply(parentSessionId);

        parent.ask(
                        (Function<ActorRef<Done>, SessionCommand>)
                                replyTo -> new ChildPausedCommand(currentSessionId, confirmationId, replyTo),
                        ASK_TIMEOUT)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOG.error(
                                "Failed to propagate pause confirmation '{}' from session '{}' to parent '{}:{}'",
                                confirmationId,
                                currentSessionId,
                                parentAgentId,
                                parentSessionId,
                                error);
                    }
                });
    }

    private Effect<SessionFact, SessionActorState> runFailed(
            final SessionActorState state, final RunFailedCommand command) {
        LOG.warn("Run failed for session {}: {}", persistenceId().id(), command.error());

        return Effect()
                // TODO: add failed event to turn events?
                .persist(new TurnCommittedFact(List.copyOf(turnEvents), command.error(), null))
                .thenRun(newState -> {
                    turnEvents.clear();
                    updateSessionStatus(newState, SessionStatus.FAILED);
                    if (newState.topology().isRoot()) {
                        self.tell(new StartNextQueuedMessageCommand());
                    }
                });
    }

    private Effect<SessionFact, SessionActorState> startNextQueuedMessage(
            final SessionActorState state, final StartNextQueuedMessageCommand command) {
        if (state.sessionState() != SessionState.IDLE || state.queue().isEmpty()) {
            return Effect().none();
        }

        final UniqueRecord<String> nextMessage = state.queue().peek();
        return Effect().persist(new StartedFact(nextMessage)).thenRun(newState -> {
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
                        (state, fact) -> SessionActorState.initial().withTopology(fact.getTopology()))
                .onEvent(
                        StartedFact.class,
                        (state, fact) -> state.dequeue()
                                .withSessionState(SessionState.TRIGGERED_RUN)
                                .withCurrentMessage(fact.getMessage()))
                .onEvent(ConfirmedFact.class, (state, fact) -> {
                    final Confirmation confirmation = fact.getConfirmation();
                    return state.isSelfConfirmation(confirmation)
                            ? state.selfResume(confirmation)
                            : state.childResume(confirmation);
                })
                .onEvent(ResumedFact.class, (state, fact) -> state.withSessionState(SessionState.RUNNING))
                .onEvent(PausedFact.class, (state, fact) -> {
                    final String childSessionId = fact.getSessionId();
                    final String confirmationId = fact.getConfirmationId();
                    if (childSessionId != null) {
                        return state.childPaused(childSessionId, confirmationId);
                    }
                    return state.selfPaused(confirmationId);
                })
                .onEvent(TurnCommittedFact.class, SessionActor::applyCommittedTurn)
                .onEvent(MessageEnqueuedFact.class, (state, fact) -> state.enqueue(fact.getMessage()))
                .onEvent(ChildStartingFact.class, (state, fact) -> state.startingChild(fact.getChild()))
                .onEvent(
                        ChildStartedFact.class,
                        (state, fact) ->
                                state.startedChild(fact.getSessionId(), new ChildSession(fact.getAgentId(), null)))
                .build();
    }

    private static SessionActorState applyCommittedTurn(final SessionActorState state, final TurnCommittedFact fact) {
        final List<Event> events = CollectionUtils.nullSafeList(fact.getEvents());
        final SessionActorState newState = state.withCommitedEvents(events);
        final String finalAnswer = fact.getFinalAnswer();
        final String failure = fact.getFailure();
        if (finalAnswer != null) {
            return newState.withRunResult(RunResult.success(finalAnswer))
                    .withSessionState(SessionState.IDLE)
                    .clearCurrentMessage();
        }
        if (failure != null) {
            return newState.withRunResult(RunResult.failure(failure))
                    .withSessionState(SessionState.IDLE)
                    .clearCurrentMessage();
        }
        if (state.sessionState() == SessionState.PAUSED) {
            return newState.withSessionState(SessionState.PAUSED);
        }
        return newState.withSessionState(SessionState.RUNNING);
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
