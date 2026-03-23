package com.agentengine.runtime.actor;

import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.pekko.PekkoSerializable;
import com.agentengine.util.pekko.actor.ShardedEntity;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;
import org.apache.pekko.persistence.typed.javadsl.SignalHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent, cluster-sharded actor managing the lifecycle of a single agent session.
 *
 * <p>
 * One actor per session. Root sessions own their event channel scope; child sessions
 * publish to the root's scope, creating a unified event stream for the entire agent graph.
 *
 * <p>
 * <b>State machine:</b>
 * <pre>
 *   IDLE → (StartRun / StartRunFromQueue) → RUNNING → (ExecutionCompleted) → IDLE
 *                                                    → (PauseRequested) → PAUSED
 *                                                    → (ExecutionFailed, unrecoverable) → IDLE
 *   PAUSED → (ResumeRun) → RUNNING
 *   RUNNING / PAUSED → (StartRun) → message queued, remains in current phase
 * </pre>
 *
 * <p>
 * <b>Child agent coordination:</b>
 * Parent spawns children via {@link Command.SpawnChildAgent}. Children notify parent on
 * completion via {@link Command.NotifyChildCompleted}. If the parent called
 * {@link Command.AwaitChildResult}, the pending Pekko ask is resolved immediately;
 * otherwise the result is cached until await is called.
 *
 * <p>
 * <b>Snapshots:</b> taken on {@link Event.RunCompleted} and {@link Event.ConfirmationRequested}
 * (turn boundaries), ensuring recovery lands at the last complete, consistent turn.
 */
public final class SessionActor extends ShardedEntity<SessionActor.Command, SessionActor.Event, SessionActor.State> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionActor.class);

    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, AssetClass.AGENT_SESSION);

    /** Maximum in-memory retry attempts for recoverable failures. Resets on actor recovery. */
    private static final int MAX_RETRIES = 3;

    // non-persistent state that can be recovered from persistent state
    private final ActorContext<Command> ctx;
    private final String agentId;
    private final String sessionId;
    private final SessionEventChannel eventChannel;
    private final AgentRunner runner;
    private int retryCount = 0;
    private String lastFinalAnswer = null;
    private final List<SessionEvent> liveTurnEvents = new ArrayList<>();
    private final Map<String, ActorRef<ChildResult>> pendingAwaitCallbacks = new HashMap<>();

    public SessionActor(final ActorContext<Command> ctx,
                        final String agentId,
                        final String sessionId,
                        final SessionEventChannel eventChannel,
                        final AgentRunner runner) {
        super(TYPE_KEY.name(), agentId + ":" + sessionId);
        this.ctx = ctx;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.eventChannel = eventChannel;
        this.runner = runner;
    }

    public static EntityTypeKey<Command> getEntityTypeKey() {
        return TYPE_KEY;
    }

    @Override
    public State emptyState() {
        return State.idle();
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(Command.Start.class, (state, cmd) -> {
                    final ActorRef<RunReceipt> replyTo = cmd.replyTo();
                    if (state.status() != AgentSession.AgentSessionStatus.PENDING_INIT) {
                        return Effect().reply(replyTo, RunReceipt.rejected("Already initialized"));
                    }
                    return Effect().persist(new Event.Started(UUID.randomUUID().toString())).thenReply(replyTo, _ -> RunReceipt.accepted());
                })
                .onCommand(Command.Resume.class, (state, cmd) -> {
                    final Map<String, String> pendingConfirmations = state.pendingConfirmationIds();
                    final String confirmationId = cmd.confirmationId();
                    final ActorRef<RunReceipt> replyTo = cmd.replyTo();
                    if (!pendingConfirmations.containsKey(confirmationId)) {
                        return Effect().reply(replyTo, RunReceipt.rejected("Invalid confirmation ID"));
                    }
                    return Effect().persist(List.of(new Event.Resumed(confirmationId))).thenRun(s -> replyTo.tell(RunReceipt.accepted()));
                }).build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(Event.Started.class, (state, event) -> {
                    channel = channelFactory.getChannel(agentId, sessionId);
                    return state.withActiveRun(event.runId());
                })
                //TODO: resume handling
                .build();
    }

    @Override
    public SignalHandler<State> signalHandler() {
        return newSignalHandlerBuilder().onSignal(PostStop.class, (state, signal) -> {
            LOG.debug("Stopping session : {} for agent : {}", sessionId, agentId);
            if (channel != null) {
                channel.complete(sessionId);
            }
        }).build();
    }

    public static EntityTypeKey<Command> getEntityTypeKey() {
        return TYPE_KEY;
    }

    public record RunReceipt(String rejectedReason) implements PekkoSerializable {
        public static RunReceipt accepted() { return new RunReceipt(null); }
        public static RunReceipt rejected(String reason) { return new RunReceipt(reason); }
    }

    public sealed interface Command extends PekkoSerializable permits Command.Start, Command.Resume, Command.Message {
        record Start(String agentId, String message, ActorRef<RunReceipt> replyTo) implements Command { }
        record Resume(String agentId, String confirmationId, Boolean confirmed, String answer, ActorRef<RunReceipt> replyTo) implements Command { }
        record Message(String childSessionId, String message, ActorRef<Ack> replyTo) implements Command { }
    }

    public record Ack(String value) implements PekkoSerializable {
        public static final Ack OK = new Ack("OK");
    }

    public interface Event extends PekkoSerializable {
        record Started(String runId) implements Event { }
        record Resumed(String confirmationId) implements Event { }
    }

    public record State(AgentSession.AgentSessionStatus status, String activeRunId, Map<String, String> pendingConfirmationIds) {
        public State {
            pendingConfirmationIds = CollectionUtils.nullSafeMutableMap(pendingConfirmationIds);
        }

        public State withStatus(AgentSession.AgentSessionStatus status) {
            return new State(status, activeRunId, pendingConfirmationIds);
        }

        public State withActiveRun(String runId) {
            return new State(status, runId, pendingConfirmationIds);
        }
    }
}
