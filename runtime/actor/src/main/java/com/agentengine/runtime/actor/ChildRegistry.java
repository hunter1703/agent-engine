package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Tracks all child worker sessions and their individual run histories. */
public record ChildRegistry(Map<String, ChildWorker> workers) implements PekkoSerializable {

    public static final ChildRegistry EMPTY = new ChildRegistry(Map.of());

    public ChildRegistry register(final String childSessionId, final ChildWorker worker) {
        final Map<String, ChildWorker> next = new HashMap<>(workers);
        next.put(childSessionId, worker);
        return new ChildRegistry(next);
    }

    public ChildRegistry update(final String childSessionId, final UnaryOperator<ChildWorker> fn) {
        final ChildWorker worker = workers.get(childSessionId);
        if (worker == null) {
            return this;
        }
        final Map<String, ChildWorker> next = new HashMap<>(workers);
        next.put(childSessionId, fn.apply(worker));
        return new ChildRegistry(next);
    }

    public Optional<ChildWorker> get(final String childSessionId) {
        return Optional.ofNullable(workers.get(childSessionId));
    }

    public boolean hasActiveRuns() {
        return workers.values().stream().anyMatch(ChildWorker::hasActiveRun);
    }

    /** A reusable child agent session that can accept multiple sequential runs. */
    public record ChildWorker(String childAgentId, Map<String, ChildRunState> runs)
            implements PekkoSerializable {

        public boolean hasActiveRun() {
            return runs.values().stream()
                    .anyMatch(state -> state instanceof ChildRunState.Active
                            || state instanceof ChildRunState.Paused);
        }

        public ChildWorker withRun(final String runId, final ChildRunState state) {
            final Map<String, ChildRunState> next = new HashMap<>(runs);
            if (state instanceof ChildRunState.Completed || state instanceof ChildRunState.Failed) {
                // Drop stale terminal states — only the latest result needs to be available
                // for AwaitChildRun. The prior result should have been consumed before a new
                // run was started (SendChildMessage rejects while a run is active).
                next.entrySet().removeIf(e ->
                        e.getValue() instanceof ChildRunState.Completed
                        || e.getValue() instanceof ChildRunState.Failed);
            }
            next.put(runId, state);
            return new ChildWorker(childAgentId, next);
        }
    }

    /** Lifecycle state of a single child run. */
    public sealed interface ChildRunState extends PekkoSerializable
            permits ChildRunState.Active, ChildRunState.Paused,
                    ChildRunState.Completed, ChildRunState.Failed {

        record Active() implements ChildRunState {}
        record Paused(Set<String> confirmationIds) implements ChildRunState {}
        record Completed(ChildRunResult result) implements ChildRunState {}
        record Failed(String reason) implements ChildRunState {}
    }

    /**
     * Identifies a specific run within a child worker session.
     * await_agent awaits a ChildRunHandle, not just a session —
     * guaranteeing the correct result is returned even for reusable workers.
     */
    public record ChildRunHandle(String childSessionId, String childRunId)
            implements PekkoSerializable {}

    /** The terminal result of a completed child run. */
    public record ChildRunResult(String output, Map<String, Object> metadata)
            implements PekkoSerializable {

        public static ChildRunResult of(final String output) {
            return new ChildRunResult(output, Map.of());
        }
    }
}
