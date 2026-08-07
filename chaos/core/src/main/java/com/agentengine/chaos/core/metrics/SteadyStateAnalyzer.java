package com.agentengine.chaos.core.metrics;

import com.agentengine.chaos.api.SteadyStateMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Aggregates raw {@link SteadyStateMetrics} snapshots into a single baseline/post-recovery
 * reading, and determines how far a snapshot has deviated from baseline.
 *
 * <p>"Recovered" has no universal definition, so {@code maxDeviationPercentage} is a caller
 * -supplied threshold rather than a hardcoded constant — experiments choose how strict recovery
 * detection should be via {@code SuccessCriterion(MAX_RECOVERY_TIME, ...)}.
 */
public final class SteadyStateAnalyzer {

    private static final double EPSILON = 1e-9;

    public SteadyStateMetrics average(final List<SteadyStateMetrics> snapshots) {
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("Cannot average an empty snapshot list");
        }
        final int n = snapshots.size();
        double successRate = 0;
        double errorRate = 0;
        long p50Nanos = 0;
        long p95Nanos = 0;
        long p99Nanos = 0;
        long journalLagNanos = 0;
        long mongoLatencyNanos = 0;
        int activeSessions = 0;
        int podRestarts = 0;

        for (final SteadyStateMetrics snapshot : snapshots) {
            successRate += snapshot.successRate();
            errorRate += snapshot.errorRate();
            p50Nanos += snapshot.p50Latency().toNanos();
            p95Nanos += snapshot.p95Latency().toNanos();
            p99Nanos += snapshot.p99Latency().toNanos();
            journalLagNanos += snapshot.eventJournalLag().toNanos();
            mongoLatencyNanos += snapshot.mongoLatency().toNanos();
            activeSessions += snapshot.activeSessions();
            podRestarts = Math.max(podRestarts, snapshot.podRestarts());
        }

        return new SteadyStateMetrics(
                successRate / n,
                Duration.ofNanos(p50Nanos / n),
                Duration.ofNanos(p95Nanos / n),
                Duration.ofNanos(p99Nanos / n),
                errorRate / n,
                activeSessions / n,
                Duration.ofNanos(journalLagNanos / n),
                Duration.ofNanos(mongoLatencyNanos / n),
                podRestarts,
                snapshots.get(snapshots.size() - 1).timestamp());
    }

    /** Deviation from baseline as a percentage, driven by the two most steady-state-defining signals. */
    public double deviationPercentage(final SteadyStateMetrics baseline, final SteadyStateMetrics candidate) {
        final double successRateDeviation = relativeDelta(baseline.successRate(), candidate.successRate());
        final double latencyDeviation = relativeDelta(
                baseline.p99Latency().toNanos(), candidate.p99Latency().toNanos());
        return Math.max(successRateDeviation, latencyDeviation) * 100.0;
    }

    /**
     * Finds the first post-recovery snapshot within {@code maxDeviationPercentage} of baseline and
     * returns the elapsed time since {@code faultRemovedAt}. Empty if the system never returned
     * within the observed snapshots.
     */
    public Optional<Duration> calculateRecoveryTime(
            final SteadyStateMetrics baseline,
            final List<SteadyStateMetrics> postFaultSnapshots,
            final Instant faultRemovedAt,
            final double maxDeviationPercentage) {
        return postFaultSnapshots.stream()
                .filter(snapshot -> deviationPercentage(baseline, snapshot) <= maxDeviationPercentage)
                .min((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .map(snapshot -> Duration.between(faultRemovedAt, snapshot.timestamp()));
    }

    private static double relativeDelta(final double baselineValue, final double candidateValue) {
        final double denominator = Math.max(Math.abs(baselineValue), EPSILON);
        return Math.abs(candidateValue - baselineValue) / denominator;
    }
}
