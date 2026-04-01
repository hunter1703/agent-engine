package com.agentengine.runtime.session;

import static com.agentengine.runtime.session.SessionActorFactory.ASK_TIMEOUT;

import com.agentengine.runtime.actor.ResumeResult;
import com.agentengine.runtime.actor.SessionEventChannel;
import com.agentengine.runtime.actor.StartSessionResult;
import com.agentengine.runtime.factories.RunnerFactory;
import com.agentengine.runtime.services.MongoSessionService;
import com.agentengine.runtime.session.commands.ExternalCommand.*;
import com.agentengine.runtime.session.commands.InternalCommand.PauseCommand;
import com.agentengine.runtime.session.commands.InternalCommand.PublishEventCommand;
import com.agentengine.runtime.session.commands.InternalCommand.ResumeChildCommand;
import com.agentengine.runtime.session.commands.InternalCommand.RunFailedCommand;
import com.agentengine.runtime.session.commands.InternalCommand.StartChildCompletedCommand;
import com.agentengine.runtime.session.commands.InternalCommand.StartNextQueuedMessageCommand;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.session.events.ChildStartedFact;
import com.agentengine.runtime.session.events.InitializedFact;
import com.agentengine.runtime.session.events.MessageEnqueuedFact;
import com.agentengine.runtime.session.events.PausedFact;
import com.agentengine.runtime.session.events.ResumedFact;
import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.runtime.session.events.SessionFact;
import com.agentengine.runtime.session.events.StartedFact;
import com.agentengine.runtime.session.events.TurnCommittedFact;
import com.agentengine.runtime.session.state.ActorState;
import com.agentengine.runtime.session.state.ChildSession;
import com.agentengine.runtime.session.state.SessionState;
import com.agentengine.runtime.session.state.SessionTopology;
import com.agentengine.runtime.utils.EventUtils;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.ExceptionUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.pekko.actor.ShardedEntity;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.CommandHandlerBuilder;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EffectBuilder;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;
import org.apache.pekko.persistence.typed.javadsl.SignalHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persistent, cluster-sharded actor that manages a single agent session. */
public final class SessionActor extends ShardedEntity<SessionCommand, SessionFact, ActorState> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionActor.class);

    public static final EntityTypeKey<SessionCommand> TYPE_KEY =
            EntityTypeKey.create(SessionCommand.class, AssetClass.AGENT_SESSION);

    private final ActorContext<SessionCommand> context;
    private final ActorRef<SessionCommand> self;
    private final SessionEventChannel eventChannel;
    private final Queue<Event> turnEvents = new ArrayDeque<>();
    private final BiFunction<String, String, EntityRef<SessionCommand>> refSupplier;
    private final RunnerFactory runnerFactory;
    private final MongoSessionService sessionService;
    private SessionRunner runner;

    public SessionActor(
            final ActorContext<SessionCommand> context,
            final String entityId,
            final SessionEventChannel eventChannel,
            final BiFunction<String, String, EntityRef<SessionCommand>> refSupplier,
            final RunnerFactory runnerFactory,
            final MongoSessionService sessionService) {
        super(TYPE_KEY.name(), entityId);
        this.context = context;
        this.self = context.getSelf();
        this.eventChannel = eventChannel;
        this.refSupplier = refSupplier;
        this.runnerFactory = runnerFactory;
        this.sessionService = sessionService;
    }

    @Override
    public ActorState emptyState() {
        return ActorState.initial();
    }

    @Override
    public CommandHandler<SessionCommand, SessionFact, ActorState> commandHandler() {
        final CommandHandlerBuilder<SessionCommand, SessionFact, ActorState> builder = newCommandHandlerBuilder();
        builder.forAnyState()
                .onCommand(InitializeCommand.class, this::initialize)
                .onCommand(StartCommand.class, this::start)
                .onCommand(ResumeCommand.class, this::resume)
                .onCommand(AwaitCommand.class, this::await)
                .onCommand(StartChildCommand.class, this::startChild)
                .onCommand(SendMessageCommand.class, this::sendMessage)
                .onCommand(PublishEventCommand.class, this::publishEvent)
                .onCommand(PauseCommand.class, this::pause)
                .onCommand(ResumeChildCommand.class, this::resumeChild)
                .onCommand(StartChildCompletedCommand.class, this::startChildCompleted)
                .onCommand(RunFailedCommand.class, this::runFailed)
                .onCommand(StartNextQueuedMessageCommand.class, this::startNextQueuedMessage);

        return builder.build();
    }

    private Effect<SessionFact, ActorState> initialize(final ActorState state, final InitializeCommand command) {
        if (state.topology() != null) {
            return Effect().none().thenReply(command.replyTo(), _ -> Done.done());
        }

        final SessionTopology topology = command.topology();
        sessionService.createSession(topology.agentId(), topology.sessionId(), topology.parentSessionId(), null);
        runner = runnerFactory.buildRunner(topology.agentId(), topology.sessionId(), self);
        return Effect().persist(new InitializedFact(topology)).thenReply(command.replyTo(), _ -> Done.done());
    }

    private Effect<SessionFact, ActorState> start(final ActorState state, final StartCommand command) {
        final SessionTopology topology = state.topology();
        return switch (state.session()) {
            case IDLE ->
                Effect()
                        .persist(new MessageEnqueuedFact(command.message()))
                        .thenRun(_ -> self.tell(new StartNextQueuedMessageCommand()))
                        .thenReply(command.replyTo(), _ -> new StartSessionResult.Accepted());
            case RUNNING, PAUSED -> {
                if (topology.isRoot()) {
                    yield Effect()
                            .persist(new MessageEnqueuedFact(command.message()))
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

    private Effect<SessionFact, ActorState> resume(final ActorState state, final ResumeCommand command) {
        if (state.session() != SessionState.PAUSED) {
            return Effect()
                    .none()
                    .thenReply(command.replyTo(), _ -> new ResumeResult.Rejected("Session is not paused"));
        }

        final String confirmationId = command.confirmationId();
        final String childSessionId = state.getPausedChild(confirmationId);
        final String answer = command.answer();
        final Boolean confirmed = command.confirmed();

        if (childSessionId != null) {
            final Optional<ChildSession> child = state.child(childSessionId);
            if (child.isEmpty()) {
                return Effect()
                        .none()
                        .thenReply(
                                command.replyTo(),
                                _ -> new ResumeResult.Rejected("Unknown child session: " + childSessionId));
            }

            final EntityRef<SessionCommand> childRef =
                    refSupplier.apply(child.get().agentId(), childSessionId);
            context.pipeToSelf(
                    childRef.ask(
                            (Function<ActorRef<ResumeResult>, SessionCommand>)
                                    replyTo -> new ResumeCommand(confirmationId, confirmed, answer, replyTo),
                            ASK_TIMEOUT),
                    (resumeResult, error) -> new ResumeChildCommand(
                            confirmationId,
                            command.replyTo(),
                            resumeResult,
                            error == null ? null : error.getMessage()));
            return Effect().none();
        }

        if (!state.isSelfConfirmationId(confirmationId)) {
            return Effect()
                    .none()
                    .thenReply(
                            command.replyTo(),
                            _ -> new ResumeResult.Rejected("No confirmation for id: " + confirmationId));
        }

        return Effect()
                .persist(new ResumedFact(confirmationId))
                .thenRun(_ -> runner.resume(confirmationId, confirmed, answer))
                .thenReply(command.replyTo(), _ -> new ResumeResult.Resumed());
    }

    private Effect<SessionFact, ActorState> resumeChild(final ActorState state, final ResumeChildCommand command) {
        if (command.error() != null) {
            return Effect()
                    .none()
                    .thenReply(
                            command.replyTo(),
                            _ -> new ResumeResult.Rejected(
                                    "Failed to forward resume to child session: " + command.error()));
        }

        if (command.result() instanceof ResumeResult.Rejected rejected) {
            return Effect().none().thenReply(command.replyTo(), _ -> rejected);
        }

        return Effect()
                .persist(new ResumedFact(command.confirmationId()))
                .thenReply(command.replyTo(), _ -> command.result());
    }

    private Effect<SessionFact, ActorState> await(final ActorState state, final AwaitCommand command) {
        final String childSessionId = command.childSessionId();
        if (childSessionId == null) {
            return switch (state.session()) {
                case PAUSED, RUNNING -> Effect().none().thenReply(command.replyTo(), _ -> RunResult.incomplete());
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
                .toCompletableFuture()
                .whenComplete((result, error) -> command.replyTo()
                        .tell(
                                error == null
                                        ? result
                                        : RunResult.failure("Failed to await child session: " + error.getMessage())));
        return Effect().none();
    }

    private Effect<SessionFact, ActorState> startChild(final ActorState state, final StartChildCommand command) {
        final String childAgentId = command.agentId();
        final String message = command.message();
        return switch (state.session()) {
            case RUNNING, PAUSED -> {
                final String childSessionId = UUID.randomUUID().toString();
                context.pipeToSelf(
                        startChildSession(state, childAgentId, childSessionId, message),
                        (result, error) -> new StartChildCompletedCommand(
                                childSessionId,
                                childAgentId,
                                command.replyTo(),
                                result,
                                error == null ? null : ExceptionUtils.getErrorMessage(error)));
                yield Effect().none();
            }
            default ->
                Effect()
                        .none()
                        .thenReply(
                                command.replyTo(),
                                _ -> new StartChildResult(
                                        null,
                                        new StartSessionResult.Rejected("The current session is in "
                                                + state.session()
                                                + " state and cannot spawn a new child")));
        };
    }

    private CompletionStage<StartSessionResult> startChildSession(
            final ActorState state, final String childAgentId, final String childSessionId, final String message) {
        final EntityRef<SessionCommand> childRef = refSupplier.apply(childAgentId, childSessionId);
        final SessionTopology childTopology = SessionTopology.child(
                childAgentId,
                childSessionId,
                state.topology().rootSessionId(),
                state.topology().sessionId(),
                state.topology().agentId());
        return childRef.ask(
                        (Function<ActorRef<Done>, SessionCommand>)
                                initReplyTo -> new InitializeCommand(childTopology, initReplyTo),
                        ASK_TIMEOUT)
                .thenCompose(_ -> childRef.ask(
                        (Function<ActorRef<StartSessionResult>, SessionCommand>)
                                startReplyTo -> new StartCommand(message, startReplyTo),
                        ASK_TIMEOUT));
    }

    private Effect<SessionFact, ActorState> startChildCompleted(
            final ActorState state, final StartChildCompletedCommand command) {
        if (command.error() != null) {
            return Effect()
                    .none()
                    .thenReply(
                            command.replyTo(),
                            _ -> new StartChildResult(
                                    command.sessionId(),
                                    new StartSessionResult.Rejected(
                                            "Failed to start child session: " + command.error())));
        }

        if (command.result() instanceof StartSessionResult.Rejected rejected) {
            return Effect()
                    .none()
                    .thenReply(command.replyTo(), _ -> new StartChildResult(command.sessionId(), rejected));
        }

        return Effect()
                .persist(new ChildStartedFact(command.sessionId(), command.agentId()))
                .thenReply(command.replyTo(), _ -> new StartChildResult(command.sessionId(), command.result()));
    }

    private Effect<SessionFact, ActorState> sendMessage(final ActorState state, final SendMessageCommand command) {
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
                            command.replyTo().tell(result);
                        }));
    }

    private Effect<SessionFact, ActorState> publishEvent(final ActorState state, final PublishEventCommand command) {
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
        return Effect()
                .persist(new TurnCommittedFact(
                        events,
                        state.nextSequence(),
                        null,
                        EventUtils.isTerminal(event)
                                ? event.content()
                                        .orElse(Content.builder().build())
                                        .text()
                                : null))
                .thenRun(newState -> {
                    final SessionTopology newTopology = newState.topology();
                    if (newTopology.isRoot()
                            && newState.session() == SessionState.IDLE
                            && !newState.queue().isEmpty()) {
                        self.tell(new StartNextQueuedMessageCommand());
                    }
                });
    }

    private Effect<SessionFact, ActorState> pause(final ActorState state, final PauseCommand command) {
        final String confirmationId = command.confirmationId();

        final EffectBuilder<SessionFact, ActorState> effect;
        if (command.forChild()) {
            effect = switch (state.session()) {
                case RUNNING, PAUSED ->
                    Effect().persist(PausedFact.childPaused(command.childSessionId(), confirmationId));
                default -> Effect().none();
            };
        } else {
            effect = switch (state.session()) {
                case RUNNING, PAUSED ->
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

    private Effect<SessionFact, ActorState> runFailed(final ActorState state, final RunFailedCommand command) {
        LOG.warn("Run failed for session {}: {}", persistenceId().id(), command.error());

        return Effect()
                .persist(new TurnCommittedFact(List.copyOf(turnEvents), state.nextSequence(), command.error(), null))
                .thenRun(newState -> {
                    turnEvents.clear();
                    final SessionTopology topology = newState.topology();
                    if (topology.isRoot()
                            && newState.session() == SessionState.IDLE
                            && !newState.queue().isEmpty()) {
                        self.tell(new StartNextQueuedMessageCommand());
                    }
                });
    }

    private Effect<SessionFact, ActorState> startNextQueuedMessage(
            final ActorState state, final StartNextQueuedMessageCommand command) {
        if (state.session() != SessionState.IDLE || state.queue().isEmpty()) {
            return Effect().none();
        }

        final String nextMessage = state.queue().peek();
        return Effect().persist(new StartedFact(nextMessage)).thenRun(_ -> runner.start(nextMessage));
    }

    @Override
    public EventHandler<ActorState, SessionFact> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(InitializedFact.class, (state, fact) -> state.withTopology(fact.getTopology()))
                .onEvent(
                        StartedFact.class,
                        (state, fact) -> state.dequeue()
                                .withSessionState(SessionState.RUNNING)
                                .withCurrentMessage(fact.getMessage()))
                .onEvent(ResumedFact.class, (state, fact) -> {
                    final String confirmationId = fact.getConfirmationId();
                    return state.isSelfConfirmationId(confirmationId)
                            ? state.selfResume(confirmationId)
                            : state.childResume(confirmationId);
                })
                .onEvent(PausedFact.class, (state, fact) -> {
                    final String childSessionId = fact.getSessionId();
                    final String confirmationId = fact.getConfirmationId();
                    if (childSessionId != null) {
                        return state.childPaused(childSessionId, confirmationId);
                    }
                    return state.selfPaused(confirmationId);
                })
                .onEvent(TurnCommittedFact.class, (state, fact) -> {
                    final ActorState newState = state.withNextSequence(
                            fact.getStartSequence() + fact.getEvents().size());
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
                    return newState;
                })
                .onEvent(MessageEnqueuedFact.class, (state, fact) -> state.enqueue(fact.getMessage()))
                .onEvent(ChildStartedFact.class, (state, fact) -> {
                    final Optional<ChildSession> existing = state.child(fact.getSessionId());
                    if (existing.isPresent()) {
                        return state.updateChild(fact.getSessionId(), ChildSession::startRun);
                    }
                    return state.registerChild(fact.getSessionId(), new ChildSession(fact.getAgentId(), null));
                })
                .build();
    }

    @Override
    public SignalHandler<ActorState> signalHandler() {
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
        return RetentionCriteria.disabled();
    }

    private void onRecoveryCompleted(final ActorState state) {
        if (state == null) {
            return;
        }

        final SessionTopology topology = state.topology();
        runner = runnerFactory.buildRunner(topology.agentId(), topology.sessionId(), self);

        switch (state.session()) {
            case RUNNING -> runner.start(state.currentMessage());
            case PAUSED -> {
                if (!topology.isRoot()) {
                    for (final String confirmationId : state.pauseState().getSelfConfirmationIds()) {
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
}
