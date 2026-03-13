package com.agentengine.engine.hitl;

import static com.google.adk.flows.llmflows.Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionPauseViewTest {

  @Test
  void shouldResolveDecisionPauseFromPendingToolConfirmationEvent() {
    final SessionPauseView pauseView = SessionPauseView
        .from(List.of(toolConfirmationEvent("run_cmd", null, System.currentTimeMillis() - 1_000L)));

    assertThat(pauseView.isPaused()).isTrue();
    assertThat(pauseView.kind()).isEqualTo(SessionPauseKind.DECISION);
    assertThat(pauseView.prompt()).isEqualTo("Approve this action?");
    assertThat(pauseView.options()).containsExactly("ALLOW", "DISALLOW");
    assertThat(pauseView.confirmationId()).isEqualTo("confirm-1");
  }

  @Test
  void shouldResolveTextPauseFromInternalHumanInputConfirmationEvent() {
    final long requestedAt = System.currentTimeMillis();
    final SessionPauseView pauseView = SessionPauseView.from(List.of(toolConfirmationEvent("request_human_input",
        Map.of("prompt", "Which city?", "kind", "TEXT", "options", List.of("Paris", "Dubai")), requestedAt)));

    assertThat(pauseView.isPaused()).isTrue();
    assertThat(pauseView.kind()).isEqualTo(SessionPauseKind.TEXT);
    assertThat(pauseView.prompt()).isEqualTo("Which city?");
    assertThat(pauseView.options()).containsExactly("Paris", "Dubai");
    assertThat(pauseView.confirmationId()).isEqualTo("confirm-1");
  }

  @Test
  void shouldIgnoreRespondedConfirmationEvents() {
    final Event confirmationEvent = toolConfirmationEvent("run_cmd", null, System.currentTimeMillis());
    final Event responseEvent = Event
        .builder().id("response-1").invocationId("inv-1").author("user").timestamp(
            System.currentTimeMillis())
        .content(Content.builder().role("user").parts(List.of(Part.builder().functionResponse(FunctionResponse.builder().id("confirm-1")
            .name(REQUEST_CONFIRMATION_FUNCTION_CALL_NAME).response(Map.of("confirmed", true)).build()).build())).build())
        .build();

    final SessionPauseView pauseView = SessionPauseView.from(List.of(confirmationEvent, responseEvent));

    assertThat(pauseView.isPaused()).isFalse();
    assertThat(pauseView.confirmationId()).isNull();
  }

  private static Event toolConfirmationEvent(final String toolName, final Map<String, Object> originalArgs, final long requestedAt) {
    final Map<String, Object> originalFunctionCall = originalArgs == null || originalArgs.isEmpty()
        ? Map.of("name", toolName)
        : Map.of("name", toolName, "args", originalArgs);
    final FunctionCall confirmationCall = FunctionCall.builder().id("confirm-1").name(REQUEST_CONFIRMATION_FUNCTION_CALL_NAME)
        .args(Map.of("originalFunctionCall", originalFunctionCall, "toolConfirmation",
            originalArgs == null || originalArgs.isEmpty()
                ? Map.of("hint", "Approve this action?")
                : Map.of("hint", originalArgs.get("prompt"), "payload",
                    Map.of("kind", originalArgs.get("kind"), "options", originalArgs.get("options")))))
        .build();
    return Event.builder().id("event-1").invocationId("inv-1").author("agent-a").timestamp(requestedAt)
        .content(Content.builder().role("model").parts(List.of(Part.builder().functionCall(confirmationCall).build())).build()).build();
  }
}
