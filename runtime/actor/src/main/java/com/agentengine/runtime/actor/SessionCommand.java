package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.Set;

/**
 * All commands understood by a SessionActor.
 *
 * <p>Split into ExternalCommand (public API, sent by callers) and
 * InternalCommand (private, sent by the runner, child actors, or the actor itself).
 * SessionActorFactory.entityRef() returns EntityRef&lt;SessionCommand&gt;; callers
 * use only ExternalCommand variants.
 */
public sealed interface SessionCommand extends PekkoSerializable
        permits SessionCommand.ExternalCommand, SessionCommand.InternalCommand {

    // ── External — public actor API ──────────────────────────────────────────

    sealed interface ExternalCommand extends SessionCommand
            permits ExternalCommand.InitializeSession,
                    ExternalCommand.StartRun, ExternalCommand.ResumeRun,
                    ExternalCommand.SpawnChild, ExternalCommand.SendChildTask,
                    ExternalCommand.AwaitChildRun {

        record InitializeSession(
                SessionTopology topology,
                ActorRef<SessionReply.InitializeResult> replyTo
        ) implements ExternalCommand {}

        record StartRun(
                String message,
                ActorRef<SessionReply.StartRunResult> replyTo
        ) implements ExternalCommand {}

        record ResumeRun(
                String confirmationId,
                Object confirmationResponse,
                ActorRef<SessionReply.ResumeResult> replyTo
        ) implements ExternalCommand {}

        record SpawnChild(
                String childAgentId,
                String message,
                ActorRef<SessionReply.SpawnResult> replyTo
        ) implements ExternalCommand {}

        record SendChildTask(
                String childSessionId,
                String message,
                ActorRef<SessionReply.SendTaskResult> replyTo
        ) implements ExternalCommand {}

        record AwaitChildRun(
                ChildRegistry.ChildRunHandle handle,
                ActorRef<SessionReply.AwaitResult> replyTo
        ) implements ExternalCommand {}
    }

    // ── Internal — not part of the public actor API ──────────────────────────

    sealed interface InternalCommand extends SessionCommand
            permits InternalCommand.NotifyChildRunCompleted,
                    InternalCommand.NotifyChildRunFailed,
                    InternalCommand.NotifyChildRunPaused,
                    InternalCommand.ExecutionEvent,
                    InternalCommand.ExecutionCompleted,
                    InternalCommand.ExecutionFailed,
                    InternalCommand.PauseRequested,
                    InternalCommand.DrainQueue,
                    InternalCommand.RecoveryCleanup {

        // Child → parent notifications (fire-and-forget)
        record NotifyChildRunCompleted(
                String childSessionId,
                String childRunId,
                ChildRegistry.ChildRunResult result
        ) implements InternalCommand {}

        record NotifyChildRunFailed(
                String childSessionId,
                String childRunId,
                String reason
        ) implements InternalCommand {}

        record NotifyChildRunPaused(
                String childSessionId,
                String childRunId,
                Set<String> confirmationIds
        ) implements InternalCommand {}

        // Runner → actor (fire-and-forget, high-frequency)
        record ExecutionEvent(String runId, SessionEvent event) implements InternalCommand {}
        record ExecutionCompleted(String runId) implements InternalCommand {}
        record ExecutionFailed(String runId, String error, boolean recoverable) implements InternalCommand {}
        record PauseRequested(String runId, Set<String> confirmationIds) implements InternalCommand {}

        // Self-messages
        record DrainQueue() implements InternalCommand {}
        record RecoveryCleanup(String runId) implements InternalCommand {}
    }
}
