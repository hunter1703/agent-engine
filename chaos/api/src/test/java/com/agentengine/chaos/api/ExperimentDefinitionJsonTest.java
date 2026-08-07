package com.agentengine.chaos.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.fault.PodKillParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExperimentDefinitionJsonTest {

    private final ObjectMapper mapper =
            new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new Jdk8Module());

    @Test
    void shouldRoundTripThroughJson() throws Exception {
        final ExperimentDefinition definition = new ExperimentDefinition(
                "runtime-pod-kill-recovery",
                "Validate session recovery after runtime pod termination",
                new TargetSelector("agent-engine", "runtime", Map.of("app", "runtime"), Optional.empty()),
                FaultType.POD_KILL,
                new PodKillParameters(1),
                Duration.ofSeconds(30),
                new BlastRadius(BlastRadiusScope.SERVICE, 1, 25.0),
                List.of(
                        new SuccessCriterion(CriterionType.ZERO_DATA_LOSS, 0.0, "No event sequence gaps"),
                        new SuccessCriterion(CriterionType.MAX_RECOVERY_TIME, 60.0, "Recovery within 60s")),
                Duration.ofSeconds(60),
                Duration.ofSeconds(60),
                Optional.of("0 */6 * * *"),
                Map.of("environment", "staging"),
                false,
                true);

        final String json = mapper.writeValueAsString(definition);
        final ExperimentDefinition deserialized = mapper.readValue(json, ExperimentDefinition.class);

        assertThat(deserialized).isEqualTo(definition);
    }
}
