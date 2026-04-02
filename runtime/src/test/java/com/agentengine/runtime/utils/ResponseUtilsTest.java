package com.agentengine.runtime.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.events.ToolConfirmation;
import org.junit.jupiter.api.Test;

class ResponseUtilsTest {

    @Test
    void shouldKeepAnswerPayloadWhenExplicitConfirmationFlagIsProvided() {
        final ToolConfirmation confirmation = ResponseUtils.buildToolConfirmation(true, "approved");

        assertThat(confirmation.confirmed()).isTrue();
        assertThat(confirmation.payload()).isEqualTo(java.util.Map.of("answer", "approved"));
    }
}
