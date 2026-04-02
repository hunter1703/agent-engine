package com.agentengine.runtime.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionUtilsTest {

    @Test
    void shouldResolvePendingRequestConfirmationIdForOriginalToolCallId() {
        final String resolvedId = SessionUtils.resolvePendingConfirmationResponseId(
                List.of(requestConfirmationEvent("confirm-1")), "tool-1");

        assertThat(resolvedId).isEqualTo("confirm-1");
    }

    @Test
    void shouldIgnoreAlreadyRespondedRequestConfirmationIds() {
        final String resolvedId = SessionUtils.resolvePendingConfirmationResponseId(
                List.of(requestConfirmationEvent("confirm-1"), confirmationResponseEvent("confirm-1")), "tool-1");

        assertThat(resolvedId).isEqualTo("tool-1");
    }

    private static com.google.adk.events.Event requestConfirmationEvent(final String confirmationId) {
        final FunctionCall originalCall = FunctionCall.builder()
                .id("tool-1")
                .name("human_in_the_loop")
                .args(Map.of("kind", "TEXT"))
                .build();
        final FunctionCall requestConfirmationCall = FunctionCall.builder()
                .id(confirmationId)
                .name(Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME)
                .args(Map.of("originalFunctionCall", originalCall, "toolConfirmation", Map.of("hint", "confirm")))
                .build();
        return com.google.adk.events.Event.builder()
                .author("model")
                .content(Content.builder()
                        .parts(List.of(Part.builder()
                                .functionCall(requestConfirmationCall)
                                .build()))
                        .build())
                .build();
    }

    private static com.google.adk.events.Event confirmationResponseEvent(final String confirmationId) {
        final FunctionResponse response = FunctionResponse.builder()
                .id(confirmationId)
                .name(Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME)
                .response(Map.of("confirmed", true))
                .build();
        return com.google.adk.events.Event.builder()
                .author("user")
                .content(Content.builder()
                        .role("user")
                        .parts(List.of(Part.builder().functionResponse(response).build()))
                        .build())
                .build();
    }
}
