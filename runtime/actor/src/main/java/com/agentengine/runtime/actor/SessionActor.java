package com.agentengine.runtime.actor;

import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.pekko.actor.ShardedEntity;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent, cluster-sharded actor managing the lifecycle of a single agent session.
 *
 * <p><b>Two-phase lifecycle:</b> Uninitialized (accepts only InitializeSession) →
 * Initialized (accepts all commands). Topology is immutable after initialization.
 *
 * <p><b>State machine:</b>
 * <pre>
 *   Idle → Running → Idle (run completed / failed)
 *                 → Paused (self HITL or child cascade)
 *   Paused → Running (resume)
 *   Any → queue (StartRun while busy)
 * </pre>
 *
 * <p><b>History:</b> Committed events are NOT stored in actor state. They are
 * persisted to the journal via TurnCommitted facts and materialized into MongoDB
 * by the SessionHistoryProjection. Snapshots are small and fast.
 */
public final class SessionActor
        extends ShardedEntity<SessionCommand, SessionFact, SessionState> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionActor.class);

    public static final EntityTypeKey<SessionCommand> TYPE_KEY =
            EntityTypeKey.create(SessionCommand.class, AssetClass.AGENT_SESSION);

    private static final int MAX_RETRIES = 3;

    private final ActorContext<SessionCommand> ctx;
    private final AgentRunner runner;
    private final SessionEventChannel eventChannel;

    // Ephemeral — not persisted, lost on crash
    private final TurnBuffer turnBuffer = TurnBuffer.create();
    private final Map<ChildRegistry.ChildRunHandle, ActorRef<SessionReply.AwaitResult>>
            pendingAwaitCallbacks = new HashMap<>();
    private int retryCount = 0;

    public SessionActor(final ActorContext<SessionCommand> ctx,
                        final String entityId,
                        final AgentRunner runner,
                        final SessionEventChannel eventChannel) {
        super(TYPE_KEY.name(), entityId);
        this.ctx = ctx;
        this.runner = runner;
        this.eventChannel = eventChannel;
    }

    @Override
    public SessionState emptyState() {
        return null; // uninitialized — actor accepts only InitializeSession
    }

    // ── Command routing ──────────────────────────────────────────────────────

    @Override
    public CommandHandler<SessionCommand, SessionFact, SessionState> commandHandler() {
        final var builder = newCommandHandlerBuilder();

        // Uninitialized phase: only InitializeSession is accepted
        builder.forNullState()
                .onCommand(SessionCommand.ExternalCommand.InitializeSession.class, this::onInitialize)
                .onAnyCommand((state, cmd) -> Effect().unhandled());

        // Initialized phase: all commands accepted
        builder.forState(state -> state != null)
                .onCommand(SessionCommand.ExternalCommand.StartRun.class,              this::onStartRun)
                .onCommand(SessionCommand.ExternalCommand.ResumeRun.class,             this::onResumeRun)
                .onCommand(SessionCommand.ExternalCommand.SpawnChild.class,            this::onSpawnChild)
                .onCommand(SessionCommand.ExternalCommand.SendChildTask.class,         this::onSendChildTask)
                .onCommand(SessionCommand.ExternalCommand.AwaitChildRun.class,         this::onAwaitChildRun)
                .onCommand(SessionCommand.InternalCommand.ExecutionEvent.class,        this::onExecutionEvent)
                .onCommand(SessionCommand.InternalCommand.ExecutionCompleted.class,    this::onExecutionCompleted)
                .onCommand(SessionCommand.InternalCommand.ExecutionFailed.class,       this::onExecutionFailed)
                .onCommand(SessionCommand.InternalCommand.PauseRequested.class,        this::onPauseRequested)
                .onCommand(SessionCommand.InternalCommand.NotifyChildRunCompleted.class, this::onChildRunCompleted)
                .onCommand(SessionCommand.InternalCommand.NotifyChildRunFailed.class,  this::onChildRunFailed)
                .onCommand(SessionCommand.InternalCommand.NotifyChildRunPaused.class,  this::onChildRunPaused)
                .onCommand(SessionCommand.InternalCommand.DrainQueue.class,            this::onDrainQueue)
                .onCommand(SessionCommand.InternalCommand.RecoveryCleanup.class,       this::onRecoveryCleanup);

        return builder.build();
    }

    // ── Event handler (pure state reconstruction — no side effects) ──────────

    @Override
    public EventHandler<SessionState, SessionFact> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(SessionFact.SessionInitialized.class,
                        (state, f) -> SessionState.initial(f.topology()))
                .onEvent(SessionFact.RunStarted.class,
                        (state, f) -> state.withExecution(
                                new ExecutionState.Running(f.runId(), 0, state.queue())))
                .onEvent(SessionFact.TurnCommitted.class,
                        (state, f) -> state.withNextSequence(
                                f.startSequence() + f.events().size()))
                .onEvent(SessionFact.RunCompleted.class,
                        (state, f) -> state.withExecution(
                                new ExecutionState.Idle(state.queue())))
                .onEvent(SessionFact.RunFailed.class,
                        (state, f) -> state.withExecution(
                                new ExecutionState.Idle(state.queue())))
                .onEvent(SessionFact.RunPaused.class,
                        (state, f) -> state.withExecution(
                                new ExecutionState.Paused(
                                        f.runId(), f.confirmationIds(), f.resumeTarget(), state.queue())))
                .onEvent(SessionFact.RunResumed.class,
                        (state, f) -> state.withExecution(
                                new ExecutionState.Running(f.runId(), 0, state.queue())))
                .onEvent(SessionFact.MessageEnqueued.class,
                        (state, f) -> state.withExecution(
                                state.execution().withQueue(state.queue().enqueue(f.message()))))
                .onEvent(SessionFact.MessageDequeued.class,
                        (state, f) -> state.withExecution(
                                state.execution().withQueue(state.queue().dequeue())))
                .onEvent(SessionFact.ChildRegistered.class,
                        (state, f) -> state.withChildRegistry(
                                state.childRegistry().register(f.childSessionId(),
                                        new ChildRegistry.ChildWorker(f.childAgentId(), Map.of()))))
                .onEvent(SessionFact.ChildRunStarted.class,
                        (state, f) -> state.withChildRegistry(
                                state.childRegistry().update(f.childSessionId(), w ->
                                        w.withRun(f.childRunId(),
                                                new ChildRegistry.ChildRun(f.childRunId(),
                                                        new ChildRegistry.ChildRunState.Active())))))
                .onEvent(SessionFact.ChildRunCompleted.class,
                        (state, f) -> state.withChildRegistry(
                                state.childRegistry().update(f.childSessionId(), w ->
                                        w.withRun(f.childRunId(),
                                                w.runs().get(f.childRunId())
                                                        .withState(new ChildRegistry.ChildRunState.Completed(f.result()))))))
                .onEvent(SessionFact.ChildRunFailed.class,
                        (state, f) -> state.withChildRegistry(
                                state.childRegistry().update(f.childSessionId(), w ->
                                        w.withRun(f.childRunId(),
                                                w.runs().get(f.childRunId())
                                                        .withState(new ChildRegistry.ChildRunState.Failed(f.reason()))))))
                .onEvent(SessionFact.ChildRunPaused.class,
                        (state, f) -> state.withChildRegistry(
                                state.childRegistry().update(f.childSessionId(), w ->
                                        w.withRun(f.childRunId(),
                                                w.runs().get(f.childRunId())
                                                        .withState(new ChildRegistry.ChildRunState.Paused(f.confirmationIds()))))))
                .build();
    }

    // ── Recovery ─────────────────────────────────────────────────────────────

    @Override
    public SignalHandler<SessionState> signalHandler() {
        return newSignalHandlerBuilder()
                .onSignal(RecoveryCompleted.class, (state, signal) -> onRecoveryCompleted(state))
                .build();
    }

    private void onRecoveryCompleted(final SessionState state) {
        if (state == null) return; // never initialized
        switch (state.execution()) {
            case ExecutionState.Running(var runId, _, _) -> {
                // In-flight execution died with the JVM. Self-send cleanup so
                // the command handler can persist RunFailed via Effect().persist().
                ctx.getSelf().tell(new SessionCommand.InternalCommand.RecoveryCleanup(runId));
            }
            case ExecutionState.Paused _ -> {
                // Paused state survives recovery. Confirmation IDs are in persisted state.
            }
            case ExecutionState.Idle _ -> {
                if (!state.queue().isEmpty()) {
                    ctx.getSelf().tell(new SessionCommand.InternalCommand.DrainQueue());
                }
            }
        }
    }

    // ── Snapshot retention ───────────────────────────────────────────────────

    @Override
    public RetentionCriteria retentionCriteria() {
        // Snapshot at natural resting points. Do NOT delete journal events —
        // the projection reads them. Journal compaction is handled by the
        // projection offset + a configurable retention window.
        return RetentionCriteria.snapshotEvery(Integer.MAX_VALUE, 2);
    }

    // ── Command handlers ─────────────────────────────────────────────────────

    private Effect<SessionFact, SessionState> onInitialize(
            final SessionState state,
            final SessionCommand.ExternalCommand.InitializeSession cmd) {
        return Effect()
                .persist(new SessionFact.SessionInitialized(cmd.topology(), Instant.now()))
                .thenRun(_ -> cmd.replyTo().tell(new SessionReply.InitializeResult.Initialized()));
    }

    private Effect<SessionFact, SessionState> onStartRun(
            final SessionState state,
            final SessionCommand.ExternalCommand.StartRun cmd) {
        return switch (state.execution()) {
            case ExecutionState.Idle _ -> {
                final var runId = UUID.randomUUID().toString();
                yield Effect()
                        .persist(new SessionFact.RunStarted(runId, cmd.message(), Instant.now()))
                        .thenRun(newState -> {
                            retryCount = 0;
                            turnBuffer.drain(); // ensure clean buffer
                            cmd.replyTo().tell(new SessionReply.StartRunResult.RunAccepted(runId));
                            runner.startRun(newState.topology(), runId, cmd.message(),
                                    ctx.getSelf());
                        });
            }
            case ExecutionState.Running _, ExecutionState.Paused _ ->
                Effect()
                        .persist(new SessionFact.MessageEnqueued(cmd.message(), Instant.now()))
                        .thenRun(newState ->
                                cmd.replyTo().tell(new SessionReply.StartRunResult.RunQueued(
                                        newState.queue().messages().size())));
        };
    }

    private Effect<SessionFact, SessionState> onResumeRun(
            final SessionState state,
            final SessionCommand.ExternalCommand.ResumeRun cmd) {
        return switch (state.execution()) {
            case ExecutionState.Paused(var runId, _, var target, _) -> switch (target) {
                case ResumeTarget.Self _ ->
                    Effect()
                            .persist(new SessionFact.RunResumed(runId, cmd.confirmationId(), Instant.now()))
                            .thenRun(newState -> {
                                retryCount = 0;
                                cmd.replyTo().tell(new SessionReply.ResumeResult.Resumed(runId));
                                runner.resumeRun(newState.topology(), runId,
                                        cmd.confirmationId(), cmd.confirmationResponse(),
                                        ctx.getSelf());
                            });
                case ResumeTarget.Child _ -> {
                    // TODO: forward ResumeRun to child actor via SessionActorFactory
                    cmd.replyTo().tell(new SessionReply.ResumeResult.Resumed(runId));
                    yield Effect().none();
                }
            };
            default -> {
                cmd.replyTo().tell(new SessionReply.ResumeResult.Rejected("Session is not paused"));
                yield Effect().none();
            }
        };
    }

    private Effect<SessionFact, SessionState> onSpawnChild(
            final SessionState state,
            final SessionCommand.ExternalCommand.SpawnChild cmd) {
        final var childSessionId = UUID.randomUUID().toString();
        final var childRunId = UUID.randomUUID().toString();
        final var handle = new ChildRegistry.ChildRunHandle(childSessionId, childRunId);
        return Effect()
                .persist(List.of(
                        new SessionFact.ChildRegistered(childSessionId, cmd.childAgentId(), Instant.now()),
                        new SessionFact.ChildRunStarted(childSessionId, childRunId, Instant.now())
                ))
                .thenRun(newState -> {
                    cmd.replyTo().tell(new SessionReply.SpawnResult.ChildSpawned(handle));
                    // TODO: dispatch child session initialization via SessionActorFactory
                });
    }

    private Effect<SessionFact, SessionState> onSendChildTask(
            final SessionState state,
            final SessionCommand.ExternalCommand.SendChildTask cmd) {
        final var worker = state.childRegistry().get(cmd.childSessionId());
        if (worker.isEmpty()) {
            cmd.replyTo().tell(new SessionReply.SendTaskResult.Rejected(
                    "Unknown child session: " + cmd.childSessionId()));
            return Effect().none();
        }
        if (worker.get().hasActiveRun()) {
            cmd.replyTo().tell(new SessionReply.SendTaskResult.Rejected(
                    "Child session is busy. Await the current run first."));
            return Effect().none();
        }
        final var childRunId = UUID.randomUUID().toString();
        final var handle = new ChildRegistry.ChildRunHandle(cmd.childSessionId(), childRunId);
        return Effect()
                .persist(new SessionFact.ChildRunStarted(cmd.childSessionId(), childRunId, Instant.now()))
                .thenRun(newState -> {
                    cmd.replyTo().tell(new SessionReply.SendTaskResult.TaskAccepted(handle));
                    // TODO: tell child actor new StartRun via SessionActorFactory
                });
    }

    private Effect<SessionFact, SessionState> onAwaitChildRun(
            final SessionState state,
            final SessionCommand.ExternalCommand.AwaitChildRun cmd) {
        final var run = state.childRegistry()
                .get(cmd.handle().childSessionId())
                .map(w -> w.runs().get(cmd.handle().childRunId()));

        switch (run.map(ChildRegistry.ChildRun::state).orElse(null)) {
            case ChildRegistry.ChildRunState.Completed c ->
                cmd.replyTo().tell(new SessionReply.AwaitResult.Completed(c.result()));
            case ChildRegistry.ChildRunState.Failed f ->
                cmd.replyTo().tell(new SessionReply.AwaitResult.Failed(f.reason()));
            case ChildRegistry.ChildRunState.Active _,
                 ChildRegistry.ChildRunState.Paused _ ->
                // Park callback; delivered when NotifyChildRunCompleted arrives
                pendingAwaitCallbacks.put(cmd.handle(), cmd.replyTo());
            case null ->
                cmd.replyTo().tell(new SessionReply.AwaitResult.Failed(
                        "Unknown child run: " + cmd.handle()));
        }
        return Effect().none();
    }

    private Effect<SessionFact, SessionState> onExecutionEvent(
            final SessionState state,
            final SessionCommand.InternalCommand.ExecutionEvent cmd) {
        // Publish to live stream immediately — no batching
        eventChannel.publish(state.rootSessionId(), cmd.event());

        // Accumulate in turn buffer; flush if full
        final boolean full = turnBuffer.add(cmd.event());
        if (full) {
            return commitPartialTurn(state, cmd.runId());
        }
        return Effect().none();
    }

    private Effect<SessionFact, SessionState> onExecutionCompleted(
            final SessionState state,
            final SessionCommand.InternalCommand.ExecutionCompleted cmd) {
        final var facts = new ArrayList<SessionFact>();
        if (!turnBuffer.isEmpty()) {
            facts.add(new SessionFact.TurnCommitted(
                    cmd.runId(), turnBuffer.drain(), state.nextSequence(), Instant.now()));
        }
        facts.add(new SessionFact.RunCompleted(cmd.runId(), Instant.now()));
        return Effect()
                .persist(facts)
                .thenRun(newState -> {
                    notifyParentIfChild(newState, cmd.runId());
                    if (shouldCompleteChannel(newState)) {
                        eventChannel.complete(newState.rootSessionId());
                    }
                    if (!newState.queue().isEmpty()) {
                        ctx.getSelf().tell(new SessionCommand.InternalCommand.DrainQueue());
                    }
                });
    }

    private Effect<SessionFact, SessionState> onExecutionFailed(
            final SessionState state,
            final SessionCommand.InternalCommand.ExecutionFailed cmd) {
        if (cmd.recoverable() && retryCount < MAX_RETRIES) {
            retryCount++;
            runner.retryRun(state.topology(), cmd.runId(), ctx.getSelf());
            return Effect().none();
        }
        final var facts = new ArrayList<SessionFact>();
        if (!turnBuffer.isEmpty()) {
            facts.add(new SessionFact.TurnCommitted(
                    cmd.runId(), turnBuffer.drain(), state.nextSequence(), Instant.now()));
        }
        facts.add(new SessionFact.RunFailed(cmd.runId(), cmd.error(), cmd.recoverable(), Instant.now()));
        return Effect()
                .persist(facts)
                .thenRun(newState -> {
                    notifyParentIfChild(newState, cmd.runId());
                    if (!newState.queue().isEmpty()) {
                        ctx.getSelf().tell(new SessionCommand.InternalCommand.DrainQueue());
                    }
                });
    }

    private Effect<SessionFact, SessionState> onPauseRequested(
            final SessionState state,
            final SessionCommand.InternalCommand.PauseRequested cmd) {
        final var facts = new ArrayList<SessionFact>();
        if (!turnBuffer.isEmpty()) {
            facts.add(new SessionFact.TurnCommitted(
                    cmd.runId(), turnBuffer.drain(), state.nextSequence(), Instant.now()));
        }
        facts.add(new SessionFact.RunPaused(
                cmd.runId(), cmd.confirmationIds(), new ResumeTarget.Self(), Instant.now()));
        return Effect().persist(facts);
    }

    private Effect<SessionFact, SessionState> onChildRunCompleted(
            final SessionState state,
            final SessionCommand.InternalCommand.NotifyChildRunCompleted cmd) {
        return Effect()
                .persist(new SessionFact.ChildRunCompleted(
                        cmd.childSessionId(), cmd.childRunId(), cmd.result(), Instant.now()))
                .thenRun(newState -> {
                    final var handle = new ChildRegistry.ChildRunHandle(cmd.childSessionId(), cmd.childRunId());
                    final var callback = pendingAwaitCallbacks.remove(handle);
                    if (callback != null) {
                        callback.tell(new SessionReply.AwaitResult.Completed(cmd.result()));
                    }
                    // TODO: publish synthetic CHILD_COMPLETED SessionEvent to root channel
                    if (shouldCompleteChannel(newState)) {
                        eventChannel.complete(newState.rootSessionId());
                    }
                    if (state.execution() instanceof ExecutionState.Paused p
                            && p.resumeTarget() instanceof ResumeTarget.Child c
                            && c.childSessionId().equals(cmd.childSessionId())) {
                        // TODO: replace RecoveryCleanup with a dedicated ChildResolved command
                        ctx.getSelf().tell(new SessionCommand.InternalCommand.RecoveryCleanup(p.runId()));
                    }
                });
    }

    private Effect<SessionFact, SessionState> onChildRunFailed(
            final SessionState state,
            final SessionCommand.InternalCommand.NotifyChildRunFailed cmd) {
        return Effect()
                .persist(new SessionFact.ChildRunFailed(
                        cmd.childSessionId(), cmd.childRunId(), cmd.reason(), Instant.now()))
                .thenRun(newState -> {
                    final var handle = new ChildRegistry.ChildRunHandle(cmd.childSessionId(), cmd.childRunId());
                    final var callback = pendingAwaitCallbacks.remove(handle);
                    if (callback != null) {
                        callback.tell(new SessionReply.AwaitResult.Failed(cmd.reason()));
                    }
                    if (shouldCompleteChannel(newState)) {
                        eventChannel.complete(newState.rootSessionId());
                    }
                });
    }

    private Effect<SessionFact, SessionState> onChildRunPaused(
            final SessionState state,
            final SessionCommand.InternalCommand.NotifyChildRunPaused cmd) {
        if (state.execution() instanceof ExecutionState.Running(var runId, _, _)) {
            final var facts = new ArrayList<SessionFact>();
            if (!turnBuffer.isEmpty()) {
                facts.add(new SessionFact.TurnCommitted(
                        runId, turnBuffer.drain(), state.nextSequence(), Instant.now()));
            }
            facts.add(new SessionFact.ChildRunPaused(
                    cmd.childSessionId(), cmd.childRunId(), cmd.confirmationIds(), Instant.now()));
            facts.add(new SessionFact.RunPaused(
                    runId, cmd.confirmationIds(),
                    new ResumeTarget.Child(cmd.childSessionId()), Instant.now()));
            return Effect().persist(facts);
        }
        return Effect()
                .persist(new SessionFact.ChildRunPaused(
                        cmd.childSessionId(), cmd.childRunId(), cmd.confirmationIds(), Instant.now()));
    }

    private Effect<SessionFact, SessionState> onDrainQueue(
            final SessionState state,
            final SessionCommand.InternalCommand.DrainQueue cmd) {
        return switch (state.execution()) {
            case ExecutionState.Idle _ when !state.queue().isEmpty() -> {
                final var message = state.queue().peek().orElseThrow();
                final var runId = UUID.randomUUID().toString();
                yield Effect()
                        .persist(List.of(
                                new SessionFact.MessageDequeued(Instant.now()),
                                new SessionFact.RunStarted(runId, message, Instant.now())
                        ))
                        .thenRun(newState -> {
                            retryCount = 0;
                            turnBuffer.drain();
                            runner.startRun(newState.topology(), runId, message,
                                    ctx.getSelf());
                        });
            }
            default -> Effect().none();
        };
    }

    private Effect<SessionFact, SessionState> onRecoveryCleanup(
            final SessionState state,
            final SessionCommand.InternalCommand.RecoveryCleanup cmd) {
        return Effect()
                .persist(new SessionFact.RunFailed(
                        cmd.runId(), "Interrupted by recovery", false, Instant.now()))
                .thenRun(newState -> {
                    if (!newState.queue().isEmpty()) {
                        ctx.getSelf().tell(new SessionCommand.InternalCommand.DrainQueue());
                    }
                });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Effect<SessionFact, SessionState> commitPartialTurn(
            final SessionState state, final String runId) {
        final var events = turnBuffer.drain();
        return Effect().persist(
                new SessionFact.TurnCommitted(runId, events, state.nextSequence(), Instant.now()));
    }

    private boolean shouldCompleteChannel(final SessionState state) {
        return state.isRoot()
                && state.execution() instanceof ExecutionState.Idle
                && !state.childRegistry().hasActiveRuns();
    }

    private void notifyParentIfChild(final SessionState state, final String runId) {
        if (state.topology().isRoot()) return;
        // TODO: notify parent actor via SessionActorFactory
    }
}
