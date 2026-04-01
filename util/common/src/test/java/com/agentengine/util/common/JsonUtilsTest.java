package com.agentengine.util.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

    @Test
    void shouldIgnoreUnknownPropertiesByDefault() {
        final BasePayload payload = JsonUtils.fromJson("""
        {
          "type": "ORCHESTRATOR",
          "parallelConfig": {
            "stoppingPolicy": "QUORUM",
            "quorum": 3
          }
        }
        """, BasePayload.class);

        assertThat(payload).isInstanceOf(OrchestratorPayload.class);
        final OrchestratorPayload orchestrator = (OrchestratorPayload) payload;
        assertThat(orchestrator.getParallel().getStoppingPolicy()).isEqualTo("ALL_COMPLETE");
        assertThat(orchestrator.getParallel().getQuorum()).isEqualTo(1);
    }

    @Test
    void shouldDeserializeDeclaredProperties() {
        final BasePayload payload = JsonUtils.fromJson("""
        {
          "type": "ORCHESTRATOR",
          "parallel": {
            "stoppingPolicy": "QUORUM",
            "quorum": 2
          }
        }
        """, BasePayload.class);

        assertThat(payload).isInstanceOf(OrchestratorPayload.class);
        final OrchestratorPayload orchestrator = (OrchestratorPayload) payload;
        assertThat(orchestrator.getParallel().getStoppingPolicy()).isEqualTo("QUORUM");
        assertThat(orchestrator.getParallel().getQuorum()).isEqualTo(2);
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "type",
            visible = true)
    @JsonSubTypes(@JsonSubTypes.Type(value = OrchestratorPayload.class, name = "ORCHESTRATOR"))
    private abstract static class BasePayload {
        private String type;

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
        }
    }

    @JsonTypeName("ORCHESTRATOR")
    private static final class OrchestratorPayload extends BasePayload {
        private ParallelPayload parallel = new ParallelPayload();

        public ParallelPayload getParallel() {
            return parallel;
        }

        public void setParallel(final ParallelPayload parallel) {
            this.parallel = parallel == null ? new ParallelPayload() : parallel;
        }
    }

    private static final class ParallelPayload {
        private String stoppingPolicy = "ALL_COMPLETE";
        private int quorum = 1;

        public String getStoppingPolicy() {
            return stoppingPolicy;
        }

        public void setStoppingPolicy(final String stoppingPolicy) {
            this.stoppingPolicy = stoppingPolicy;
        }

        public int getQuorum() {
            return quorum;
        }

        public void setQuorum(final int quorum) {
            this.quorum = quorum;
        }
    }
}
