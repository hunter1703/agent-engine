package com.agentengine.runtime.session;

import static com.agentengine.runtime.session.SessionActorFactory.ASK_TIMEOUT;

import com.agentengine.runtime.actor.ConfirmResult;
import com.agentengine.runtime.actor.SessionEventChannel;
import com.agentengine.runtime.actor.StartSessionResult;
import com.agentengine.runtime.session.state.*;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.runtime.factories.RunnerFactory;
import com.agentengine.runtime.services.MongoSessionService;
import com.agentengine.runtime.session.commands.ExternalCommand.*;
import com.agentengine.runtime.session.commands.InternalCommand.*;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.session.events.*;
import com.agentengine.runtime.utils.EventUtils;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.ExceptionUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.pekko.actor.ShardedEntity;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import io.reactivex.rxjava3.core.Maybe;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persistent, cluster-sharded actor that manages a single agent session. */
public final class SessionActor extends ShardedEntity<SessionCommand, SessionFact, SessionActorState> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionActor.class);

    public static final EntityTypeKey<SessionCommand> TYPE_KEY =
            EntityTypeKey.create(SessionCommand.class, AssetClass.AGENT_SESSION);

    private final ActorContext<SessionCommand> context;
    private final ActorRef<SessionCommand> self;
    private final int snapshotThreshold;
    private final SessionEventChannel eventChannel;
    private final Queue<Event> turnEvents = new ArrayDeque<>();
    private final BiFunction<String, String, EntityRef<SessionCommand>> refSupplier;
    private final RunnerFactory runnerFactory;
    private final MongoSessionService sessionService;
    private SessionRunner runner;

    public SessionActor(
            final ActorContext<SessionCommand> context,
            final String entityId,
            final int snapshotThreshold,
            final SessionEventChannel eventChannel,
            final BiFunction<String, String, EntityRef<SessionCommand>> refSupplier,
            final RunnerFactory runnerFactory,
            final MongoSessionService sessionService) {
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
            final Maybe<Session> session = sessionService.getSession(topology.agentId(), AgentSession.DEFAULT_USER_ID, topology.sessionId(), Optional.empty());
            if (session.blockingGet() != null) {
                sessionService.deleteSession(topology.agentId(), AgentSession.DEFAULT_USER_ID, topology.sessionId());
            }
        }
        init(topology);
        switch (sessionState) {
            case TRIGGERED_RUN -> {
                // re-start with message; as the recovery state was mid first turn
                runner.start(state.currentMessage().getRecord());
            }
            case RUNNING -> {
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
            }
            case PAUSED -> {
                if (!topology.isRoot()) {
                    for (final String confirmationId : state.pauseState().getPendingSelfConfirmationIds()) {
                        propagateSelfPauseToParent(topology, confirmationId);
                    }
                }
            }
            case IDLE -> {
                if (!state.queue().isEmpty()) {
                    self.tell(new StartNextQueuedMessageCommand());
                }
            }
        }
    }

    @Override
    public CommandHandler<SessionCommand, SessionFact, SessionActorState> commandHandler() {
        final CommandHandlerBuilder<SessionCommand, SessionFact, SessionActorState> builder = newCommandHandlerBuilder();
        builder.forAnyState()
                .onCommand(InitializeCommand.class, this::initialize)
                .onCommand(StartCommand.class, this::start)
                .onCommand(ConfirmCommand.class, this::confirm)
                .onCommand(ConfirmChildCommand.class, this::confirmChild)
                .onCommand(ResumeCommand.class, (state, _) -> {
                    final Collection<Confirmation> confirmations = state.getAllReceivedConfirmations();
                    return Effect().persist(new ResumedFact()).thenRun(_ -> runner.resume(confirmations));
                })
                .onCommand(AwaitCommand.class, this::await)
                .onCommand(StartChildCommand.class, this::startChild)
                .onCommand(SendMessageCommand.class, this::sendMessage)
                .onCommand(PublishEventCommand.class, this::publishEvent)
                .onCommand(PauseCommand.class, this::pause)
                .onCommand(StartChildCompletedCommand.class, this::startChildCompleted)
                .onCommand(RunFailedCommand.class, this::runFailed)
                .onCommand(StartNextQueuedMessageCommand.class, this::startNextQueuedMessage);

        return builder.build();
    }

    private Effect<SessionFact, SessionActorState> initialize(final SessionActorState state, final InitializeCommand command) {
        final SessionTopology topology = command.topology();
        return Effect().persist(new InitializedFact(topology)).thenRun(_ -> init(topology)).thenReply(command.replyTo(), _ -> Done.done());
    }

    private void init(final SessionTopology topology) {
        final Maybe<Session> session = sessionService.getSession(topology.agentId(), AgentSession.DEFAULT_USER_ID, topology.sessionId(), Optional.empty());
        if (session.blockingGet() == null) {
            sessionService.createSession(topology.agentId(), topology.sessionId(), topology.parentSessionId(), null);
        }
        runner = runnerFactory.buildRunner(topology.agentId(), topology.sessionId(), self);
    }

    private Effect<SessionFact, SessionActorState> start(final SessionActorState state, final StartCommand command) {
        final SessionTopology topology = state.topology();
        final UniqueRecord<String> message = command.message();
        final UniqueRecord<String> currentMessage = state.currentMessage();
        boolean isDuplicate = Objects.equals(currentMessage, message) || state.queue().contains(message);
        return switch (state.sessionState()) {
            case IDLE -> {
                if (isDuplicate) {
                    // if message present in queue but still in idle state something is wrong and trigger to the message processing loop
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

    private Effect<SessionFact, SessionActorState> confirm(final SessionActorState state, final ConfirmCommand command) {
        final Confirmation confirmation = command.confirmation();
        final String childSessionId = state.getPausedChild(confirmation);

        final ActorRef<ConfirmResult> replyTo = command.replyTo();
        if (childSessionId != null) {
            final Optional<ChildSession> child = state.child(childSessionId);
            if (child.isEmpty()) {
                return Effect()
                        .none()
                        .thenReply(
                                replyTo,
                                _ -> new ConfirmResult.Rejected("Unknown child session: " + childSessionId));
            }

            final EntityRef<SessionCommand> childRef =
                    refSupplier.apply(child.get().agentId(), childSessionId);
            context.pipeToSelf(
                    childRef.ask(
                            (Function<ActorRef<ConfirmResult>, SessionCommand>)
                                    askReplyTo -> new ConfirmCommand(confirmation, askReplyTo),
                            ASK_TIMEOUT),
                    // processing result via command and not inline because completable future thread would be different then actor thread so cannot access actor specific abstractions like persist, etc.
                    (resumeResult, error) -> new ConfirmChildCommand(
                            confirmation,
                            replyTo,
                            resumeResult,
                            error == null ? null : error.getMessage()));
            return Effect().none();
        }

        if (!state.isSelfConfirmation(confirmation)) {
            return Effect()
                    .none()
                    .thenReply(replyTo, _ -> new ConfirmResult.UnknownConfirmationId());
        }

        return confirmed(replyTo, confirmation);
    }

    private Effect<SessionFact, SessionActorState> confirmChild(final SessionActorState state, final ConfirmChildCommand command) {
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

        return confirmed(replyTo, command.confirmation());
    }

    private ReplyEffect<SessionFact, SessionActorState> confirmed(final ActorRef<ConfirmResult> replyTo, final Confirmation confirmation) {
        return Effect().persist(new ConfirmedFact(confirmation)).thenRun(newState -> {
            if (newState.allConfirmationsReceived() && newState.sessionState() == SessionState.PAUSED) {
                self.tell(new ResumeCommand());
            }
        }).thenReply(replyTo, _ -> new ConfirmResult.Accepted());
    }

    private Effect<SessionFact, SessionActorState> await(final SessionActorState state, final AwaitCommand command) {
        final String childSessionId = command.childSessionId();
        if (childSessionId == null) {
            // await is on current session
            return switch (state.sessionState()) {
                case PAUSED, TRIGGERED_RUN, RUNNING -> Effect().none().thenReply(command.replyTo(), _ -> RunResult.incomplete());
                default -> Effect().none().thenReply(command.replyTo(), _ -> state.lastResult());
            };
        }

        final Optional<ChildSession> child = state.child(childSessionId);
        if (child.isEmpty()) {
            return Effect()
                    .none()
                    .thenReply(command.replyTo(), _ -> RunResult.failure("Unknown child session: " + childSessionId));
        }

        final EntityRef<SessionCommand> childRef = refSupplier.apply(child.get().agentId(), childSessionId);
        childRef.ask(
                        (Function<ActorRef<RunResult>, SessionCommand>) replyTo -> new AwaitCommand(null, replyTo),
                        ASK_TIMEOUT)
                .whenComplete((result, error) -> command.replyTo()
                        .tell(error == null
                                ? result
                                : RunResult.failure("Failed to await child session: " + error.getMessage())));
        return Effect().none();
    }

    private Effect<SessionFact, SessionActorState> startChild(final SessionActorState state, final StartChildCommand command) {
        final String childAgentId = command.agentId();
        final UniqueRecord<String> commandMessage = command.message();
        final String childSessionId = commandMessage.getId();
        final String message = commandMessage.getRecord();
        if (state.child(childSessionId).isPresent()) {
            // a child has already started; it is a duplicate request
            return Effect().none().thenReply(command.replyTo(), _ -> new StartChildResult(childSessionId, new StartSessionResult.Accepted()));
        }
        return switch (state.sessionState()) {
            case RUNNING -> Effect().persist(new ChildStartingFact(new StartingChild(command.agentId(), childSessionId, commandMessage))).thenRun(_ -> {
                startChildSession(state, childAgentId, childSessionId, message).whenComplete((result, error) -> {
                    self.tell(new StartChildCompletedCommand(
                            childSessionId,
                            childAgentId,
                            command.replyTo(),
                            result,
                            error == null ? null : ExceptionUtils.getErrorMessage(error)));
                });
            });
            default ->
                Effect()
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
            final SessionActorState state, final String childAgentId, final String childSessionId, final String message) {
        final EntityRef<SessionCommand> childRef = refSupplier.apply(childAgentId, childSessionId);
        final SessionTopology topology = state.topology();
        final SessionTopology childTopology = SessionTopology.child(
                childAgentId,
                childSessionId,
                topology.rootSessionId(),
                topology.sessionId(),
                topology.agentId());
        return childRef.ask(
                        (Function<ActorRef<Done>, SessionCommand>)
                                initReplyTo -> new InitializeCommand(childTopology, initReplyTo),
                        ASK_TIMEOUT)
                .thenCompose(_ -> {
                    final UniqueRecord<String> uniqueMessage = new UniqueRecord<>(message);
                    //TODO: add retry to send same unique message if some transient error
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

    private Effect<SessionFact, SessionActorState> sendMessage(final SessionActorState state, final SendMessageCommand command) {
        final String childSessionId = command.sessionId();
        final Optional<ChildSession> child = state.child(childSessionId);
        if (child.isEmpty()) {
            return Effect()
                    .none()
                    .thenReply(
                            command.replyTo(),
                            _ -> new StartSessionResult.Rejected("Unknown child session: " + childSessionId));
        }

        final EntityRef<SessionCommand> childRef = refSupplier.apply(child.get().agentId(), childSessionId);
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
        final SessionTopology topology = state.topology();
        final Event event = command.event();
        final SessionEvent sessionEvent = SessionEventUtils.toSessionEvent(
                topology.rootSessionId(), topology.parentSessionId(), topology.sessionId(), event);
        eventChannel.publish(topology.rootSessionId(), sessionEvent).whenComplete((ignored, error) -> {
            if (error != null) {
                LOG.error("Failed to publish live event for rootSessionId={}", topology.rootSessionId(), error);
            }
        });

        turnEvents.add(event);

        if (!event.turnComplete().orElse(false)) {
            return Effect().none();
        }
        final ArrayList<Event> events = new ArrayList<>(turnEvents);
        turnEvents.clear();

        if (state.isDuplicateTurn(events)) {
            return Effect().none();
        }
        return Effect()
                .persist(new TurnCommittedFact(
                        events,
                        null,
                        EventUtils.isTerminal(event)
                                ? event.content()
                                        .orElse(Content.builder().build())
                                        .text()
                                : null))
                .thenRun(newState -> {
                    final SessionTopology newTopology = newState.topology();
                    if (newTopology.isRoot()
                            && newState.sessionState() == SessionState.IDLE
                            && !newState.queue().isEmpty()) {
                        self.tell(new StartNextQueuedMessageCommand());
                    }
                });
    }

    private Effect<SessionFact, SessionActorState> pause(final SessionActorState state, final PauseCommand command) {
        final String confirmationId = command.confirmationId();

        final EffectBuilder<SessionFact, SessionActorState> effect;
        if (command.forChild()) {
            effect = switch (state.sessionState()) {
                case TRIGGERED_RUN, PAUSED, RUNNING ->
                    Effect().persist(PausedFact.childPaused(command.childSessionId(), confirmationId));
                default -> Effect().none();
            };
        } else {
            effect = switch (state.sessionState()) {
                case TRIGGERED_RUN, PAUSED, RUNNING ->
                    Effect()
                            .persist(PausedFact.selfPaused(confirmationId))
                            .thenRun(newState -> propagateSelfPauseToParent(newState.topology(), confirmationId));
                default -> Effect().none();
            };
        }
        return effect.thenReply(command.replyTo(), _ -> Done.done());
    }

    private void propagateSelfPauseToParent(final SessionTopology topology, final String confirmationId) {
        if (topology.isRoot()) {
            return;
        }

        final String currentSessionId = topology.sessionId();
        final String parentSessionId = topology.parentSessionId();
        final String parentAgentId = topology.parentAgentId();
        final EntityRef<SessionCommand> parent = refSupplier.apply(parentAgentId, parentSessionId);

        parent.ask(
                        (Function<ActorRef<Done>, SessionCommand>)
                                replyTo -> PauseCommand.child(currentSessionId, confirmationId, replyTo),
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

    private Effect<SessionFact, SessionActorState> runFailed(final SessionActorState state, final RunFailedCommand command) {
        LOG.warn("Run failed for session {}: {}", persistenceId().id(), command.error());

        return Effect()
                //TODO: add failed event to turn events?
                .persist(new TurnCommittedFact(List.copyOf(turnEvents), command.error(), null))
                .thenRun(newState -> {
                    turnEvents.clear();
                    final SessionTopology topology = newState.topology();
                    if (topology.isRoot()) {
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
        return Effect().persist(new StartedFact(nextMessage)).thenRun(_ -> runner.start(nextMessage.getRecord()));
    }

    @Override
    public EventHandler<SessionActorState, SessionFact> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(InitializedFact.class, (state, fact) -> SessionActorState.initial().withTopology(fact.getTopology()))
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
                .onEvent(TurnCommittedFact.class, (state, fact) -> {
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
                    return newState.withSessionState(SessionState.RUNNING);
                })
                .onEvent(MessageEnqueuedFact.class, (state, fact) -> state.enqueue(fact.getMessage()))
                .onEvent(ChildStartingFact.class, (state, fact) -> state.startingChild(fact.getChild()))
                .onEvent(ChildStartedFact.class, (state, fact) -> state.startedChild(fact.getSessionId(), new ChildSession(fact.getAgentId(), null)))
                .build();
    }
}
