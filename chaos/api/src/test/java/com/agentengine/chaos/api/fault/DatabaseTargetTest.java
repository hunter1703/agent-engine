package com.agentengine.chaos.api.fault;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseTargetTest {

    @Test
    void shouldParseKnownAndUnknownValues() {
        assertThat(DatabaseTarget.valueOfOrDefault("postgresql")).isEqualTo(DatabaseTarget.POSTGRESQL);
        assertThat(DatabaseTarget.valueOfOrDefault("MONGODB")).isEqualTo(DatabaseTarget.MONGODB);
        assertThat(DatabaseTarget.valueOfOrDefault("qdrant")).isEqualTo(DatabaseTarget.UNKNOWN);
        assertThat(DatabaseTarget.valueOfOrDefault(null)).isEqualTo(DatabaseTarget.UNKNOWN);
    }
}
