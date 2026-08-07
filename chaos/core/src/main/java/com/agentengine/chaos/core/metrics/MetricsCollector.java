package com.agentengine.chaos.core.metrics;

import com.agentengine.chaos.api.SteadyStateMetrics;
import com.agentengine.chaos.api.TargetSelector;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects {@link SteadyStateMetrics} snapshots from Prometheus and the runtime health endpoint.
 * A snapshot is only absent when every underlying query failed (e.g. Prometheus itself is
 * unreachable); individual missing series fall back to zero so one broken exporter doesn't sink
 * an otherwise-healthy baseline.
 */
public final class MetricsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsCollector.class);

    private final PrometheusClient prometheusClient;
    private final RuntimeHealthClient runtimeHealthClient;
    private final MetricsQueries queries;
    private final SteadyStateAnalyzer analyzer;

    public MetricsCollector(
            final PrometheusClient prometheusClient,
            final RuntimeHealthClient runtimeHealthClient,
            final MetricsQueries queries,
            final SteadyStateAnalyzer analyzer) {
        this.prometheusClient = prometheusClient;
        this.runtimeHealthClient = runtimeHealthClient;
        this.queries = queries;
        this.analyzer = analyzer;
    }

    /** A single point-in-time read of all steady-state metrics. */
    public Optional<SteadyStateMetrics> collectSnapshot(final TargetSelector target) {
        final Optional<Double> successRate = prometheusClient.queryScalar(queries.successRateQuery());
        final Optional<Double> p50 = prometheusClient.queryScalar(queries.p50LatencyQuery());
        final Optional<Double> p95 = prometheusClient.queryScalar(queries.p95LatencyQuery());
        final Optional<Double> p99 = prometheusClient.queryScalar(queries.p99LatencyQuery());
        final Optional<Double> errorRate = prometheusClient.queryScalar(queries.errorRateQuery());
        final Optional<Double> podRestarts = prometheusClient.queryScalar(queries.podRestartsQuery());
        final Optional<Double> journalLag = prometheusClient.queryScalar(queries.eventJournalLagQuery());
        final Optional<Double> mongoLatency = prometheusClient.queryScalar(queries.mongoLatencyQuery());
        final OptionalInt activeSessions = runtimeHealthClient.activeSessionCount();

        final boolean allMissing = successRate.isEmpty()
                && p50.isEmpty()
                && p95.isEmpty()
                && p99.isEmpty()
                && errorRate.isEmpty()
                && podRestarts.isEmpty()
                && journalLag.isEmpty()
                && mongoLatency.isEmpty()
                && activeSessions.isEmpty();
        if (allMissing) {
            LOG.warn("All metrics queries failed for target {}; treating snapshot as unavailable", target);
            return Optional.empty();
        }

        return Optional.of(new SteadyStateMetrics(
                successRate.orElse(0.0),
                secondsToDuration(p50),
                secondsToDuration(p95),
                secondsToDuration(p99),
                errorRate.orElse(0.0),
                activeSessions.orElse(0),
                secondsToDuration(journalLag),
                secondsToDuration(mongoLatency),
                podRestarts.orElse(0.0).intValue(),
                Instant.now()));
    }

    /** Task 4.2 — steady-state baseline, polled across {@code observationWindow} before injection. */
    public Optional<SteadyStateMetrics> collectBaseline(
            final TargetSelector target, final Duration observationWindow, final Duration pollInterval) {
        return average(pollWindow(target, observationWindow, pollInterval));
    }

    /** Task 4.3 — post-recovery snapshot, polled across {@code recoveryWindow} after fault removal. */
    public Optional<SteadyStateMetrics> collectPostRecovery(
            final TargetSelector target, final Duration recoveryWindow, final Duration pollInterval) {
        return average(pollWindow(target, recoveryWindow, pollInterval));
    }

    /**
     * Raw runtime polling across a fixed window (fault-active or recovery); every successful
     * snapshot is kept. Callers that need per-snapshot timestamps (e.g. {@link
     * SteadyStateAnalyzer#calculateRecoveryTime}) use this directly instead of the averaged
     * convenience methods above.
     */
    public List<SteadyStateMetrics> pollWindow(
            final TargetSelector target, final Duration totalDuration, final Duration pollInterval) {
        final List<SteadyStateMetrics> snapshots = new ArrayList<>();
        final Instant deadline = Instant.now().plus(totalDuration);
        do {
            collectSnapshot(target).ifPresent(snapshots::add);
            if (Instant.now().isBefore(deadline)) {
                sleep(pollInterval);
            }
        } while (Instant.now().isBefore(deadline));
        return snapshots;
    }

    private Optional<SteadyStateMetrics> average(final List<SteadyStateMetrics> snapshots) {
        if (snapshots.isEmpty()) {
            LOG.warn("No successful metric snapshots collected");
            return Optional.empty();
        }
        return Optional.of(analyzer.average(snapshots));
    }

    private static Duration secondsToDuration(final Optional<Double> seconds) {
        return seconds.map(value -> Duration.ofNanos(Math.round(value * 1_000_000_000L)))
                .orElse(Duration.ZERO);
    }

    private static void sleep(final Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
