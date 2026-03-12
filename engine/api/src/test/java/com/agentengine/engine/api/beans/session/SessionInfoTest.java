package com.agentengine.engine.api.beans.session;

import static com.google.adk.flows.llmflows.Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class SessionInfoTest {

  @Test
  void shouldRoundTripSessionWhenConvertingFromAndToSession() {
    final Event event =
        Event.builder()
            .id("event-1")
            .invocationId("inv-1")
            .author("user")
            .timestamp(Instant.now().toEpochMilli())
            .content(Content.builder().role("user").parts(List.of(Part.fromText("hello"))).build())
            .build();
    final Session sourceSession =
        Session.builder("session-1")
            .appName("agent-a")
            .userId("user-a")
            .state(new ConcurrentHashMap<>(Map.of("key", "value")))
            .events(List.of(event))
            .lastUpdateTime(Instant.now())
            .build();

    final SessionInfo sessionInfo = SessionInfo.fromSession(sourceSession);
    final Session restored = sessionInfo.toSession();

    assertThat(restored.id()).isEqualTo(sourceSession.id());
    assertThat(restored.appName()).isEqualTo(sourceSession.appName());
    assertThat(restored.userId()).isEqualTo(sourceSession.userId());
    assertThat(restored.state()).containsEntry("key", "value");
    assertThat(restored.events()).hasSize(1);
    assertThat(restored.events().getFirst().id()).isEqualTo("event-1");
  }

  @Test
  void shouldFallbackToEmptyEventsWhenEventsJsonIsBlankOrNullLiteral() {
    final SessionInfo blank = new SessionInfo();
    blank.setEventsJson("   ");
    assertThat(blank.getEvents()).isEmpty();

    final SessionInfo nullLiteral = new SessionInfo();
    nullLiteral.setEventsJson("null");
    assertThat(nullLiteral.getEvents()).isEmpty();
  }

  @Test
  void shouldResolveDecisionPauseFromPendingToolConfirmationEvent() {
    final SessionInfo sessionInfo =
        SessionInfo.fromSession(
            Session.builder("session-1")
                .appName("agent-a")
                .userId("user-a")
                .state(new ConcurrentHashMap<>())
                .events(List.of(toolConfirmationEvent("run_cmd", null, System.currentTimeMillis() - 1_000L)))
                .lastUpdateTime(Instant.now())
                .build());

    final SessionPauseView pauseView = sessionInfo.getSessionPauseView();

    assertThat(pauseView.isPaused()).isTrue();
    assertThat(pauseView.kind()).isEqualTo(SessionPauseKind.DECISION);
    assertThat(pauseView.prompt()).isEqualTo("Approve this action?");
    assertThat(pauseView.options()).containsExactly("ALLOW", "DISALLOW");
  }

  @Test
  void shouldResolveTextPauseFromInternalHumanInputConfirmationEvent() {
    final long requestedAt = System.currentTimeMillis();
    final SessionInfo sessionInfo =
        SessionInfo.fromSession(
            Session.builder("session-1")
                .appName("agent-a")
                .userId("user-a")
                .state(new ConcurrentHashMap<>())
                .events(
                    List.of(
                        toolConfirmationEvent(
                            "request_human_input",
                            Map.of(
                                "prompt", "Which city?",
                                "kind", "TEXT",
                                "options", List.of("Paris", "Dubai")),
                            requestedAt)))
                .lastUpdateTime(Instant.now())
                .build());

    final SessionPauseView pauseView = sessionInfo.getSessionPauseView();

    assertThat(pauseView.isPaused()).isTrue();
    assertThat(pauseView.kind()).isEqualTo(SessionPauseKind.TEXT);
    assertThat(pauseView.prompt()).isEqualTo("Which city?");
    assertThat(pauseView.options()).containsExactly("Paris", "Dubai");
    assertThat(pauseView.confirmationId()).isEqualTo("confirm-1");
  }

  @Test
  void shouldIgnoreRespondedConfirmationEvents() {
    final Event confirmationEvent = toolConfirmationEvent("run_cmd", null, System.currentTimeMillis());
    final Event responseEvent =
        Event.builder()
            .id("response-1")
            .invocationId("inv-1")
            .author("user")
            .timestamp(System.currentTimeMillis())
            .content(
                Content.builder()
                    .role("user")
                    .parts(
                        List.of(
                            Part.builder()
                                .functionResponse(
                                    com.google.genai.types.FunctionResponse.builder()
                                        .id("confirm-1")
                                        .name(REQUEST_CONFIRMATION_FUNCTION_CALL_NAME)
                                        .response(Map.of("confirmed", true))
                                        .build())
                                .build()))
                    .build())
            .build();

    final SessionInfo sessionInfo =
        SessionInfo.fromSession(
            Session.builder("session-1")
                .appName("agent-a")
                .userId("user-a")
                .state(new ConcurrentHashMap<>())
                .events(List.of(confirmationEvent, responseEvent))
                .lastUpdateTime(Instant.now())
                .build());

    assertThat(sessionInfo.getSessionPauseView().isPaused()).isFalse();
  }

  private static Event toolConfirmationEvent(
      final String toolName,
      final Map<String, Object> originalArgs,
      final long requestedAt) {
    final Map<String, Object> originalFunctionCall =
        originalArgs == null || originalArgs.isEmpty()
            ? Map.of("name", toolName)
            : Map.of("name", toolName, "args", originalArgs);
    final FunctionCall confirmationCall =
        FunctionCall.builder()
            .id("confirm-1")
            .name(REQUEST_CONFIRMATION_FUNCTION_CALL_NAME)
            .args(
                Map.of(
                    "originalFunctionCall", originalFunctionCall,
                    "toolConfirmation",
                        originalArgs == null || originalArgs.isEmpty()
                            ? Map.of("hint", "Approve this action?")
                            : Map.of(
                                "hint", originalArgs.get("prompt"),
                                "payload", Map.of("kind", originalArgs.get("kind"), "options", originalArgs.get("options")))))
            .build();
    return Event.builder()
        .id("event-1")
        .invocationId("inv-1")
        .author("agent-a")
        .timestamp(requestedAt)
        .content(
            Content.builder()
                .role("model")
                .parts(List.of(Part.builder().functionCall(confirmationCall).build()))
                .build())
        .build();
  }
}
