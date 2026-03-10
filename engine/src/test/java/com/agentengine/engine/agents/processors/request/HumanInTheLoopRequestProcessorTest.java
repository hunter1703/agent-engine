package com.agentengine.engine.agents.processors.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.guardrails.GuardrailConstants;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.SessionState;
import com.agentengine.engine.utils.SessionStateUtils;
import com.agentengine.engine.utils.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class HumanInTheLoopRequestProcessorTest {

  @Test
  void shouldAppendResumeMessageForClarificationAnswer() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final SessionState sessionState = new SessionState();
    sessionState.markPaused(
        "What destination city?",
        List.of(),
        "user_clarification",
        "inv-1",
        Instant.now().toEpochMilli());
    state.put("sessionState", sessionState);
    final Session session =
        Session.builder("session-1")
            .appName("agent-1")
            .userId("default")
            .state(state)
            .events(new ArrayList<>())
            .lastUpdateTime(Instant.now())
            .build();
    final InvocationContext context = mock(InvocationContext.class);
    when(context.invocationId()).thenReturn("inv-2");
    when(context.session()).thenReturn(session);
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("Dubai")))
                        .build()))
            .build();

    final RequestProcessor.RequestProcessingResult result =
        HumanInTheLoopRequestProcessor.INSTANCE.processRequest(context, request).blockingGet();
    final List<Event> events = StreamSupport.stream(result.events().spliterator(), false).toList();

    assertThat(events).isEmpty();
    assertThat(SessionStateUtils.isPaused(context)).isFalse();
    assertThat(result.updatedRequest().contents()).hasSize(2);
    assertThat(result.updatedRequest().contents().get(1).text())
        .contains("Resuming after clarification question: 'What destination city?'.")
        .contains("Use this clarification answer: 'Dubai'.");
  }

  @Test
  void shouldResumeToolConfirmationOnNewInvocationWithUserAnswer() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final SessionState sessionState = new SessionState();
    sessionState.markPaused(
        "Tool confirmation required",
        List.of(),
        "tool_confirmation",
        "inv-1",
        Instant.now().toEpochMilli());
    state.put("sessionState", sessionState);

    final FunctionCall pendingCall =
        FunctionCall.builder()
            .id("confirm-1")
            .name("adk_request_confirmation")
            .args(Map.of())
            .build();
    final Event pendingEvent =
        Event.builder()
            .id(Event.generateEventId())
            .invocationId("inv-1")
            .author("agent-1")
            .content(
                Optional.of(
                    Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder().functionCall(pendingCall).build()))
                        .build()))
            .build();

    final Session session =
        Session.builder("session-1")
            .appName("agent-1")
            .userId("default")
            .state(state)
            .events(new ArrayList<>(List.of(pendingEvent)))
            .lastUpdateTime(Instant.now())
            .build();
    final InvocationContext context = mock(InvocationContext.class);
    when(context.invocationId()).thenReturn("inv-2");
    when(context.session()).thenReturn(session);
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                    List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("__AE_TOOL_DECISION__:APPROVE")))
                        .build()))
            .build();

    final RequestProcessor.RequestProcessingResult result =
        HumanInTheLoopRequestProcessor.INSTANCE.processRequest(context, request).blockingGet();
    final List<Event> events = StreamSupport.stream(result.events().spliterator(), false).toList();

    assertThat(events).isEmpty();
    assertThat(SessionStateUtils.isPaused(context)).isFalse();
    final List<Part> parts = result.updatedRequest().contents().getFirst().parts().orElse(List.of());
    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().functionResponse()).isPresent();
    assertThat(parts.getFirst().functionResponse().get().name()).contains("adk_request_confirmation");
    final Map<String, Object> response =
        parts.getFirst().functionResponse().get().response().orElse(Map.of());
    assertThat(response).containsEntry("confirmed", true);
    assertThat(response).containsEntry("invocation_id", "inv-1");
  }

  @Test
  void shouldFallbackToTextResumeWhenPendingConfirmationIdMissing() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final SessionState sessionState = new SessionState();
    sessionState.markPaused(
        "Tool confirmation required",
        List.of(),
        "tool_confirmation",
        "inv-1",
        Instant.now().toEpochMilli());
    state.put("sessionState", sessionState);

    final Session session =
        Session.builder("session-1")
            .appName("agent-1")
            .userId("default")
            .state(state)
            .events(new ArrayList<>())
            .lastUpdateTime(Instant.now())
            .build();
    final InvocationContext context = mock(InvocationContext.class);
    when(context.invocationId()).thenReturn("inv-2");
    when(context.session()).thenReturn(session);
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("__AE_TOOL_DECISION__:APPROVE")))
                        .build()))
            .build();

    final RequestProcessor.RequestProcessingResult result =
        HumanInTheLoopRequestProcessor.INSTANCE.processRequest(context, request).blockingGet();
    final List<Event> events = StreamSupport.stream(result.events().spliterator(), false).toList();

    assertThat(events).isEmpty();
    assertThat(SessionStateUtils.isPaused(context)).isFalse();
    assertThat(result.updatedRequest().contents()).hasSize(2);
    assertThat(result.updatedRequest().contents().get(1).text())
        .containsIgnoringCase("approved");
  }

  @Test
  void shouldIncludePausePromptInResumeMessageWhenNoConfirmationIdFound() {
    final InvocationContext ctx = mockContextWithPause(
        "tool_confirmation",
        "Awaiting approval for: run_cmd",
        null,
        "inv-1");
    final LlmRequest request =
        LlmRequest.builder()
            .contents(List.of(
                Content.builder()
                    .role("user")
                    .parts(List.of(Part.fromText("__AE_TOOL_DECISION__:APPROVE")))
                    .build()))
            .build();

    final RequestProcessor.RequestProcessingResult result =
        HumanInTheLoopRequestProcessor.INSTANCE.processRequest(ctx, request).blockingGet();

    final String resumeMessage = result.updatedRequest().contents().get(1).text();
    assertThat(resumeMessage).contains("Awaiting approval for: run_cmd");
  }

  @Test
  void shouldBuildApprovalMessageWithToolNameWhenConfirmationApproved() {
    final InvocationContext ctx = mockContextWithPause(
        "tool_confirmation",
        "Approve run_cmd?",
        "run_cmd",
        "inv-1");
    final LlmRequest request =
        LlmRequest.builder()
            .contents(List.of(
                Content.builder()
                    .role("user")
                    .parts(List.of(Part.fromText("__AE_TOOL_DECISION__:APPROVE")))
                    .build()))
            .build();

    final RequestProcessor.RequestProcessingResult result =
        HumanInTheLoopRequestProcessor.INSTANCE.processRequest(ctx, request).blockingGet();

    final String msg = result.updatedRequest().contents().get(1).text();
    assertThat(msg).contains("run_cmd");
    assertThat(msg).containsIgnoringCase("approved");
    assertThat(msg).containsIgnoringCase("execute");
    assertThat(msg).containsIgnoringCase("immediately");
  }

  @Test
  void shouldClearGuardrailViolationAndLastToolCallOnApprovedConfirmationFallback() {
    final InvocationContext ctx =
        mockContextWithPause("tool_confirmation", "Approve run_cmd?", "run_cmd", "inv-1");
    RunStateUtils.getState(ctx).updateLastToolCall("run_cmd{command=echo ok}");
    RunStateUtils.getState(ctx)
        .addViolation(
            Violation.builder(GuardrailConstants.Code.TOOL_POLICY)
                .message("confirmation required")
                .correctionMessage("confirmation required")
                .build());
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("__AE_TOOL_DECISION__:APPROVE")))
                        .build()))
            .build();

    HumanInTheLoopRequestProcessor.INSTANCE.processRequest(ctx, request).blockingGet();

    assertThat(RunStateUtils.getState(ctx).getViolations()).isEmpty();
    assertThat(RunStateUtils.getState(ctx).getLastToolCall()).isNull();
    assertThat(SessionStateUtils.getState(ctx).getApprovedToolName()).isEqualTo("run_cmd");
  }

  @Test
  void shouldBuildRejectionMessageWithToolNameWhenConfirmationRejected() {
    final InvocationContext ctx = mockContextWithPause(
        "tool_confirmation",
        "Approve run_cmd?",
        "run_cmd",
        "inv-1");
    final LlmRequest request =
        LlmRequest.builder()
            .contents(List.of(
                Content.builder()
                    .role("user")
                    .parts(List.of(Part.fromText("__AE_TOOL_DECISION__:REJECT")))
                    .build()))
            .build();

    final RequestProcessor.RequestProcessingResult result =
        HumanInTheLoopRequestProcessor.INSTANCE.processRequest(ctx, request).blockingGet();

    final String msg = result.updatedRequest().contents().get(1).text();
    assertThat(msg).contains("run_cmd");
    assertThat(msg).containsIgnoringCase("rejected");
    assertThat(msg).containsIgnoringCase("do not");
  }

  @Test
  void shouldMapRejectionAnswerToDeniedConfirmation() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final SessionState sessionState = new SessionState();
    sessionState.markPaused(
        "Tool confirmation required",
        List.of(),
        "tool_confirmation",
        "inv-1",
        Instant.now().toEpochMilli());
    state.put("sessionState", sessionState);

    final FunctionCall pendingCall =
        FunctionCall.builder()
            .id("confirm-reject-1")
            .name("adk_request_confirmation")
            .args(Map.of())
            .build();
    final Event pendingEvent =
        Event.builder()
            .id(Event.generateEventId())
            .invocationId("inv-1")
            .author("agent-1")
            .content(
                Optional.of(
                    Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder().functionCall(pendingCall).build()))
                        .build()))
            .build();

    final Session session =
        Session.builder("session-1")
            .appName("agent-1")
            .userId("default")
            .state(state)
            .events(new ArrayList<>(List.of(pendingEvent)))
            .lastUpdateTime(Instant.now())
            .build();
    final InvocationContext context = mock(InvocationContext.class);
    when(context.invocationId()).thenReturn("inv-2");
    when(context.session()).thenReturn(session);
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("__AE_TOOL_DECISION__:REJECT")))
                        .build()))
            .build();

    final RequestProcessor.RequestProcessingResult result =
        HumanInTheLoopRequestProcessor.INSTANCE.processRequest(context, request).blockingGet();

    final Map<String, Object> response =
        result.updatedRequest()
            .contents()
            .getFirst()
            .parts()
            .orElse(List.of())
            .getFirst()
            .functionResponse()
            .orElseThrow()
            .response()
            .orElse(Map.of());
    assertThat(response).containsEntry("confirmed", false);
  }

  @Test
  void shouldHonorExplicitRejectDecisionMarkerForConfirmationResponse() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final SessionState sessionState = new SessionState();
    sessionState.markPaused(
        "Tool confirmation required",
        List.of(),
        "tool_confirmation",
        "inv-1",
        Instant.now().toEpochMilli());
    state.put("sessionState", sessionState);

    final FunctionCall pendingCall =
        FunctionCall.builder()
            .id("confirm-explicit-reject")
            .name("adk_request_confirmation")
            .args(Map.of())
            .build();
    final Event pendingEvent =
        Event.builder()
            .id(Event.generateEventId())
            .invocationId("inv-1")
            .author("agent-1")
            .content(
                Optional.of(
                    Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder().functionCall(pendingCall).build()))
                        .build()))
            .build();

    final Session session =
        Session.builder("session-1")
            .appName("agent-1")
            .userId("default")
            .state(state)
            .events(new ArrayList<>(List.of(pendingEvent)))
            .lastUpdateTime(Instant.now())
            .build();
    final InvocationContext context = mock(InvocationContext.class);
    when(context.invocationId()).thenReturn("inv-2");
    when(context.session()).thenReturn(session);
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(
                            List.of(
                                Part.fromText(
                                    "__AE_TOOL_DECISION__:REJECT\nyes approve and continue")))
                        .build()))
            .build();

    final RequestProcessor.RequestProcessingResult result =
        HumanInTheLoopRequestProcessor.INSTANCE.processRequest(context, request).blockingGet();

    final Map<String, Object> response =
        result.updatedRequest()
            .contents()
            .getFirst()
            .parts()
            .orElse(List.of())
            .getFirst()
            .functionResponse()
            .orElseThrow()
            .response()
            .orElse(Map.of());
    assertThat(response).containsEntry("confirmed", false);
  }

  @Test
  void shouldHonorExplicitApproveDecisionMarkerOnFallbackPath() {
    final InvocationContext ctx = mockContextWithPause(
        "tool_confirmation",
        "Approve run_cmd?",
        "run_cmd",
        "inv-1");
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("__AE_TOOL_DECISION__:APPROVE")))
                        .build()))
            .build();

    HumanInTheLoopRequestProcessor.INSTANCE.processRequest(ctx, request).blockingGet();

    assertThat(SessionStateUtils.getState(ctx).getApprovedToolName()).isEqualTo("run_cmd");
  }

  @Test
  void shouldPruneHandledConfirmationEventsWhenFallbackResumeIsUsed() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final SessionState sessionState = new SessionState();
    sessionState.markPaused(
        "Tool confirmation required",
        List.of(),
        "tool_confirmation",
        "inv-1",
        Instant.now().toEpochMilli(),
        "run_cmd");
    state.put("sessionState", sessionState);

    final FunctionCall pendingCall =
        FunctionCall.builder()
            .id("confirm-prune-1")
            .name("adk_request_confirmation")
            .args(Map.of())
            .build();
    final Event confirmationCallEvent =
        Event.builder()
            .id(Event.generateEventId())
            .invocationId("inv-1")
            .author("agent-1")
            .content(
                Optional.of(
                    Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder().functionCall(pendingCall).build()))
                        .build()))
            .build();
    final Event confirmationResponseEvent =
        Event.builder()
            .id(Event.generateEventId())
            .invocationId("inv-1")
            .author("user")
            .content(
                Optional.of(
                    Content.builder()
                        .role("user")
                        .parts(
                            List.of(
                                Part.builder()
                                    .functionResponse(
                                        com.google.genai.types.FunctionResponse.builder()
                                            .id("confirm-prune-1")
                                            .name("adk_request_confirmation")
                                            .response(Map.of("confirmed", false))
                                            .build())
                                    .build()))
                        .build()))
            .build();

    final Session session =
        Session.builder("session-prune")
            .appName("agent-1")
            .userId("default")
            .state(state)
            .events(new ArrayList<>(List.of(confirmationCallEvent, confirmationResponseEvent)))
            .lastUpdateTime(Instant.now())
            .build();
    final InvocationContext context = mock(InvocationContext.class);
    when(context.invocationId()).thenReturn("inv-2");
    when(context.session()).thenReturn(session);
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("__AE_TOOL_DECISION__:APPROVE")))
                        .build()))
            .build();

    HumanInTheLoopRequestProcessor.INSTANCE.processRequest(context, request).blockingGet();

    assertThat(session.events()).isEmpty();
  }

  /**
   * Creates a mock InvocationContext with a paused SessionState.
   * No pending adk_request_confirmation function call is added to events (fallback path).
   *
   * @param pauseReason  the pause reason code (e.g. "tool_confirmation")
   * @param pausePrompt  the pause prompt stored in state
   * @param pendingToolName  the tool name to store (may be null)
   * @param pauseInvocationId  the invocation ID at pause time
   */
  private static InvocationContext mockContextWithPause(
      final String pauseReason,
      final String pausePrompt,
      final String pendingToolName,
      final String pauseInvocationId) {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final SessionState sessionState = new SessionState();
    sessionState.markPaused(
        pausePrompt,
        List.of(),
        pauseReason,
        pauseInvocationId == null ? "inv-pause" : pauseInvocationId,
        Instant.now().toEpochMilli(),
        pendingToolName);
    state.put("sessionState", sessionState);

    final Session session =
        Session.builder("session-mock")
            .appName("agent-mock")
            .userId("default")
            .state(state)
            .events(new ArrayList<>())
            .lastUpdateTime(Instant.now())
            .build();
    final InvocationContext context = mock(InvocationContext.class);
    when(context.invocationId()).thenReturn("inv-resume");
    when(context.session()).thenReturn(session);
    return context;
  }
}
