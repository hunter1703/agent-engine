package com.agentengine.runtime.actor;

import com.agentengine.util.agents.beans.CorrectionMetadata;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionEventUtilsTest {

    @Test
    void shouldExtractCorrectionMetadataWhenEventIsCorrectionEvent() {
        final SessionEvent event = minimalEvent().withMetadata(Map.of(
                SessionEventUtils.CORRECTION, true,
                SessionEventUtils.CORRECTION_TYPE, "TOOL_CALL",
                SessionEventUtils.CORRECTION_CODE, "SCHEMA_MISMATCH",
                SessionEventUtils.CORRECTION_MESSAGE, "Missing required field"
        ));

        final CorrectionMetadata metadata = SessionEventUtils.extractCorrectionMetadata(event);

        assertThat(metadata).isNotNull();
        assertThat(metadata.type()).isEqualTo("TOOL_CALL");
        assertThat(metadata.code()).isEqualTo("SCHEMA_MISMATCH");
        assertThat(metadata.message()).isEqualTo("Missing required field");
    }

    @Test
    void shouldReturnNullWhenEventIsNotCorrectionEvent() {
        final SessionEvent event = minimalEvent().withMetadata(Map.of("other", "value"));

        assertThat(SessionEventUtils.extractCorrectionMetadata(event)).isNull();
    }

    private static SessionEvent minimalEvent() {
        return new SessionEvent("id", null, null, "session", "run",
                "agent", null, false, false, null, 0L, 0L, Map.of());
    }
}
