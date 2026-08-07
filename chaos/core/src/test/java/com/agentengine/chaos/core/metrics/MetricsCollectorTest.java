package com.agentengine.chaos.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.SteadyStateMetrics;
import com.agentengine.chaos.api.TargetSelector;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsCollectorTest {

    private static final TargetSelector TARGET =
            new TargetSelector("agent-engine", "runtime", Map.of(), Optional.empty());
    private static final String SUCCESSFUL_PROMETHEUS_BODY = """
            {"status":"success","data":{"resultType":"vector","result":[{"metric":{},"value":[1735689600,"0.5"]}]}}""";
    private static final String EMPTY_PROMETHEUS_BODY = """
            {"status":"success","data":{"resultType":"vector","result":[]}}""";

    private MockWebServer server;
    private PrometheusClient prometheusClient;
    private MetricsCollector collector;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        prometheusClient = new PrometheusClient(
                new OkHttpClient(), server.url("/").toString().replaceAll("/$", ""));
        collector = new MetricsCollector(
                prometheusClient, () -> OptionalInt.of(7), MetricsQueries.defaults(), new SteadyStateAnalyzer());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldCollectSnapshotWhenPrometheusRespondsForAllQueries() {
        enqueueResponses(SUCCESSFUL_PROMETHEUS_BODY, 8);

        final Optional<SteadyStateMetrics> snapshot = collector.collectSnapshot(TARGET);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().successRate()).isEqualTo(0.5);
        assertThat(snapshot.get().activeSessions()).isEqualTo(7);
    }

    @Test
    void shouldReturnEmptyWhenAllQueriesFailAndHealthEndpointHasNoData() {
        enqueueResponses(EMPTY_PROMETHEUS_BODY, 8);
        final MetricsCollector collectorWithoutHealth = new MetricsCollector(
                prometheusClient, OptionalInt::empty, MetricsQueries.defaults(), new SteadyStateAnalyzer());

        assertThat(collectorWithoutHealth.collectSnapshot(TARGET)).isEmpty();
    }

    @Test
    void shouldAverageMultipleSnapshotsWhenCollectingBaseline() {
        enqueueResponses(SUCCESSFUL_PROMETHEUS_BODY, 16);

        final Optional<SteadyStateMetrics> baseline =
                collector.collectBaseline(TARGET, Duration.ofMillis(20), Duration.ofMillis(10));

        assertThat(baseline).isPresent();
        assertThat(baseline.get().successRate()).isEqualTo(0.5);
    }

    @Test
    void shouldPollMultipleSnapshotsAcrossWindow() {
        enqueueResponses(SUCCESSFUL_PROMETHEUS_BODY, 24);

        final List<SteadyStateMetrics> snapshots =
                collector.pollWindow(TARGET, Duration.ofMillis(30), Duration.ofMillis(10));

        assertThat(snapshots).isNotEmpty();
    }

    private void enqueueResponses(final String body, final int count) {
        for (int i = 0; i < count; i++) {
            server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
        }
    }
}
