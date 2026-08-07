package com.agentengine.chaos.api.fault;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FaultParametersJsonTest {

    private final ObjectMapper mapper =
            new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new Jdk8Module());

    @Test
    void shouldRoundTripPodKillParameters() throws Exception {
        assertRoundTrips(new PodKillParameters(2));
    }

    @Test
    void shouldRoundTripNetworkLatencyParameters() throws Exception {
        assertRoundTrips(new NetworkLatencyParameters(Duration.ofSeconds(2)));
    }

    @Test
    void shouldRoundTripNetworkPartitionParameters() throws Exception {
        assertRoundTrips(new NetworkPartitionParameters(List.of("mongodb", "postgresql")));
    }

    @Test
    void shouldRoundTripPacketLossParameters() throws Exception {
        assertRoundTrips(new PacketLossParameters(25.0));
    }

    @Test
    void shouldRoundTripCpuStressParameters() throws Exception {
        assertRoundTrips(new CpuStressParameters(80));
    }

    @Test
    void shouldRoundTripMemoryStressParameters() throws Exception {
        assertRoundTrips(new MemoryStressParameters("512Mi"));
    }

    @Test
    void shouldRoundTripDiskStressParameters() throws Exception {
        assertRoundTrips(new DiskStressParameters("100mb"));
    }

    @Test
    void shouldRoundTripDatabaseFailureParameters() throws Exception {
        assertRoundTrips(new DatabaseFailureParameters(DatabaseTarget.POSTGRESQL));
    }

    @Test
    void shouldRoundTripQdrantFailureParametersWithLatency() throws Exception {
        assertRoundTrips(new QdrantFailureParameters(Optional.of(Duration.ofMillis(500))));
    }

    @Test
    void shouldRoundTripQdrantFailureParametersWithFullBlock() throws Exception {
        assertRoundTrips(new QdrantFailureParameters(Optional.empty()));
    }

    @Test
    void shouldRoundTripLlmProviderLatencyParameters() throws Exception {
        assertRoundTrips(new LlmProviderLatencyParameters(Duration.ofSeconds(5)));
    }

    @Test
    void shouldRoundTripMessageDelayParameters() throws Exception {
        assertRoundTrips(new MessageDelayParameters(Duration.ofSeconds(1), 0.5));
    }

    @Test
    void shouldRoundTripMessageDropParameters() throws Exception {
        assertRoundTrips(new MessageDropParameters(0.1));
    }

    private void assertRoundTrips(final FaultParameters parameters) throws Exception {
        final String json = mapper.writeValueAsString(parameters);
        assertThat(json)
                .contains("\"parametersType\":\"" + parameters.getClass().getSimpleName() + "\"");

        final FaultParameters deserialized = mapper.readValue(json, FaultParameters.class);
        assertThat(deserialized).isEqualTo(parameters);
    }
}
