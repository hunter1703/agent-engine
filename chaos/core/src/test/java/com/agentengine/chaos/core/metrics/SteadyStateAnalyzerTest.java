package com.agentengine.chaos.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.chaos.api.SteadyStateMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SteadyStateAnalyzerTest {

    private final SteadyStateAnalyzer analyzer = new SteadyStateAnalyzer();

    @Test
    void shouldAverageSnapshotsAcrossAllFields() {
        final Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        final Instant t2 = Instant.parse("2026-01-01T00:00:10Z");

        final SteadyStateMetrics a = metrics(0.98, 100, 1, 3, t1);
        final SteadyStateMetrics b = metrics(1.00, 200, 3, 5, t2);

        final SteadyStateMetrics averaged = analyzer.average(List.of(a, b));

        assertThat(averaged.successRate()).isEqualTo(0.99);
        assertThat(averaged.p99Latency()).isEqualTo(Duration.ofMillis(150));
        assertThat(averaged.activeSessions()).isEqualTo(2);
        assertThat(averaged.podRestarts()).isEqualTo(5);
        assertThat(averaged.timestamp()).isEqualTo(t2);
    }

    @Test
    void shouldRejectEmptySnapshotList() {
        assertThatThrownBy(() -> analyzer.average(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReportZeroDeviationForIdenticalMetrics() {
        final SteadyStateMetrics baseline = metrics(0.99, 100, 2, 0, Instant.now());
        assertThat(analyzer.deviationPercentage(baseline, baseline)).isZero();
    }

    @Test
    void shouldReportHighDeviationWhenSuccessRateOrLatencyDiverge() {
        final SteadyStateMetrics baseline = metrics(0.99, 100, 2, 0, Instant.now());
        final SteadyStateMetrics duringOutage = metrics(0.10, 5000, 2, 0, Instant.now());

        assertThat(analyzer.deviationPercentage(baseline, duringOutage)).isGreaterThan(50.0);
    }

    @Test
    void shouldFindFirstSnapshotWithinDeviationThresholdAsRecoveryPoint() {
        final SteadyStateMetrics baseline = metrics(0.99, 100, 2, 0, Instant.now());
        final Instant removedAt = Instant.parse("2026-01-01T00:00:00Z");

        final SteadyStateMetrics stillDegraded = metrics(0.50, 4000, 2, 0, removedAt.plusSeconds(5));
        final SteadyStateMetrics recovered = metrics(0.985, 110, 2, 0, removedAt.plusSeconds(15));

        final Optional<Duration> recoveryTime =
                analyzer.calculateRecoveryTime(baseline, List.of(stillDegraded, recovered), removedAt, 10.0);

        assertThat(recoveryTime).contains(Duration.ofSeconds(15));
    }

    @Test
    void shouldReturnEmptyWhenNoSnapshotEverRecovers() {
        final SteadyStateMetrics baseline = metrics(0.99, 100, 2, 0, Instant.now());
        final Instant removedAt = Instant.parse("2026-01-01T00:00:00Z");
        final SteadyStateMetrics stillDegraded = metrics(0.50, 4000, 2, 0, removedAt.plusSeconds(5));

        final Optional<Duration> recoveryTime =
                analyzer.calculateRecoveryTime(baseline, List.of(stillDegraded), removedAt, 5.0);

        assertThat(recoveryTime).isEmpty();
    }

    private static SteadyStateMetrics metrics(
            final double successRate,
            final long p99Millis,
            final int activeSessions,
            final int podRestarts,
            final Instant timestamp) {
        return new SteadyStateMetrics(
                successRate,
                Duration.ofMillis(p99Millis / 2),
                Duration.ofMillis(p99Millis - p99Millis / 4),
                Duration.ofMillis(p99Millis),
                1 - successRate,
                activeSessions,
                Duration.ZERO,
                Duration.ZERO,
                podRestarts,
                timestamp);
    }
}
