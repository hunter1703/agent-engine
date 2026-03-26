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
        final var next = new HashMap<>(workers);
        next.put(childSessionId, worker);
        return new ChildRegistry(Map.copyOf(next));
    }

    public ChildRegistry update(final String childSessionId, final UnaryOperator<ChildWorker> fn) {
        final var worker = workers.get(childSessionId);
        if (worker == null) return this;
        final var next = new HashMap<>(workers);
        next.put(childSessionId, fn.apply(worker));
        return new ChildRegistry(Map.copyOf(next));
    }

    public Optional<ChildWorker> get(final String childSessionId) {
        return Optional.ofNullable(workers.get(childSessionId));
    }

    public boolean hasActiveRuns() {
        return workers.values().stream().anyMatch(ChildWorker::hasActiveRun);
    }

    // ── Nested types ────────────────────────────────────────────────────────

    /** A reusable child agent session that can accept multiple sequential runs. */
    public record ChildWorker(String childAgentId, Map<String, ChildRun> runs)
            implements PekkoSerializable {

        public boolean hasActiveRun() {
            return runs.values().stream()
                    .anyMatch(r -> r.state() instanceof ChildRunState.Active
                            || r.state() instanceof ChildRunState.Paused);
        }

        public ChildWorker withRun(final String runId, final ChildRun run) {
            final var next = new HashMap<>(runs);
            next.put(runId, run);
            return new ChildWorker(childAgentId, Map.copyOf(next));
        }
    }

    /** A single execution run within a child worker. */
    public record ChildRun(String runId, ChildRunState state) implements PekkoSerializable {
        public ChildRun withState(final ChildRunState newState) {
            return new ChildRun(runId, newState);
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
