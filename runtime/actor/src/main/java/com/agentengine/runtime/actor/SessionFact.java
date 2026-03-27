package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Sealed interface for all persisted journal facts.
 *
 * <p>Design principle: persist coordination decisions and turn boundaries only.
 * No accumulated history — events are carried in TurnCommitted and consumed
 * by the Pekko Projection; they are never stored in actor state or snapshots.
 *
 * <p>All facts implement PekkoSerializable for Jackson CBOR serialization.
 */
public sealed interface SessionFact extends PekkoSerializable
        permits SessionFact.SessionInitialized,
                SessionFact.RunStarted, SessionFact.TurnCommitted,
                SessionFact.RunPaused, SessionFact.RunResumed,
                SessionFact.RunCompleted, SessionFact.RunFailed,
                SessionFact.MessageEnqueued, SessionFact.MessageDequeued,
                SessionFact.ChildRegistered, SessionFact.ChildRunStarted,
                SessionFact.ChildRunCompleted, SessionFact.ChildRunFailed,
                SessionFact.ChildRunPaused {

    // ── Session lifecycle ────────────────────────────────────────────────────

    record SessionInitialized(SessionTopology topology, Instant timestamp) implements SessionFact {}

    // ── Run lifecycle ────────────────────────────────────────────────────────

    record RunStarted(String runId, String message, Instant timestamp) implements SessionFact {}

    /**
     * Carries committed turn events to the Pekko Projection.
     * startSequence is the sequence number of the first event in the batch;
     * each subsequent event gets startSequence + index.
     * Events are NOT stored in actor state after this fact is applied.
     */
    record TurnCommitted(
            String runId,
            List<SessionEvent> events,
            long startSequence,
            Instant timestamp
    ) implements SessionFact {}

    record RunPaused(
            String runId,
            Set<String> confirmationIds,
            ResumeTarget resumeTarget,
            Instant timestamp
    ) implements SessionFact {}

    record RunResumed(String runId, String confirmationId, Instant timestamp) implements SessionFact {}
    record RunCompleted(String runId, Instant timestamp) implements SessionFact {}
    record RunFailed(String runId, String reason, boolean recoverable, Instant timestamp) implements SessionFact {}

    // ── Queue ────────────────────────────────────────────────────────────────

    record MessageEnqueued(String message, Instant timestamp) implements SessionFact {}
    record MessageDequeued(Instant timestamp) implements SessionFact {}

    // ── Child lifecycle ──────────────────────────────────────────────────────

    record ChildRegistered(String childSessionId, String childAgentId, Instant timestamp) implements SessionFact {}
    record ChildRunStarted(String childSessionId, String childRunId, Instant timestamp) implements SessionFact {}

    record ChildRunCompleted(
            String childSessionId,
            String childRunId,
            ChildRegistry.ChildRunResult result,
            Instant timestamp
    ) implements SessionFact {}

    record ChildRunFailed(
            String childSessionId,
            String childRunId,
            String reason,
            Instant timestamp
    ) implements SessionFact {}

    record ChildRunPaused(
            String childSessionId,
            String childRunId,
            Set<String> confirmationIds,
            Instant timestamp
    ) implements SessionFact {}
}
