package com.agentengine.runtime.actor;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SessionFactTest {

    @Test
    void shouldSetCurrentTimestampWhenFactCreatedWithoutTimestamp() {
        final Instant before = Instant.now();
        final SessionFact.Started fact = new SessionFact.Started("runState-1");
        final Instant after = Instant.now();

        assertThat(fact.timestamp()).isBetween(before, after);
    }

    @Test
    void shouldUseProvidedTimestampWhenFactCreatedFromSerializedData() {
        final Instant expected = Instant.parse("2026-01-01T00:00:00Z");
        final SessionFact.Started fact = new SessionFact.Started("runState-1", expected);

        assertThat(fact.timestamp()).isEqualTo(expected);
    }
}
