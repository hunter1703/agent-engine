package com.agentengine.engine.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.utils.SessionState;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class HumanInTheLoopPluginTest {

  @Test
  void shouldNoOpWhenSessionIsNotPaused() {
    final InvocationContext context = mock(InvocationContext.class);
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final Session session =
        Session.builder("session-1")
            .appName("agent-1")
            .userId("default")
            .state(state)
            .events(new ArrayList<>())
            .lastUpdateTime(Instant.now())
            .build();
    when(context.invocationId()).thenReturn("inv-1");
    when(context.session()).thenReturn(session);
    final CallbackContext callbackContext = callbackContext(context);
    final LlmRequest.Builder requestBuilder =
        LlmRequest.builder().contents(List.of(textContent("hello")));

    final HumanInTheLoopPlugin plugin = new HumanInTheLoopPlugin();
    final LlmResponse response = plugin.beforeModelCallback(callbackContext, requestBuilder).blockingGet();

    assertThat(response).isNull();
    assertThat(requestBuilder.build().contents()).hasSize(1);
    assertThat(requestBuilder.build().contents().getFirst().text()).isEqualTo("hello");
  }

  @Test
  void shouldShortCircuitWhenToolConfirmationRunsInPauseInvocation() {
    final InvocationContext context =
        pausedContext("inv-1", "inv-1", "tool_confirmation", "Tool confirmation required");
    final CallbackContext callbackContext = callbackContext(context);
    final LlmRequest.Builder requestBuilder =
        LlmRequest.builder().contents(List.of(textContent("run command now")));

    final HumanInTheLoopPlugin plugin = new HumanInTheLoopPlugin();
    final LlmResponse response = plugin.beforeModelCallback(callbackContext, requestBuilder).blockingGet();

    verify(context).setEndInvocation(true);
    assertThat(response.content()).isPresent();
    assertThat(response.content().get().text()).contains("Tool confirmation required");
  }

  @Test
  void shouldShortCircuitWhenPausedWithoutAnswer() {
    final InvocationContext context =
        pausedContext("inv-2", "inv-1", "user_clarification", "What destination city?");
    final CallbackContext callbackContext = callbackContext(context);
    final LlmRequest.Builder requestBuilder =
        LlmRequest.builder().contents(List.of(textContent("   ")));

    final HumanInTheLoopPlugin plugin = new HumanInTheLoopPlugin();
    final LlmResponse response = plugin.beforeModelCallback(callbackContext, requestBuilder).blockingGet();

    verify(context).setEndInvocation(true);
    assertThat(response.content()).isPresent();
    assertThat(response.content().get().text()).contains("No clarification answer was provided.");
  }

  @Test
  void shouldShortCircuitWhenToolConfirmationMissingExplicitDecisionMarker() {
    final InvocationContext context =
        pausedContext("inv-2", "inv-1", "tool_confirmation", "Tool confirmation required");
    final CallbackContext callbackContext = callbackContext(context);
    final LlmRequest.Builder requestBuilder =
        LlmRequest.builder().contents(List.of(textContent("yes approve")));

    final HumanInTheLoopPlugin plugin = new HumanInTheLoopPlugin();
    final LlmResponse response = plugin.beforeModelCallback(callbackContext, requestBuilder).blockingGet();

    verify(context).setEndInvocation(true);
    assertThat(response.content()).isPresent();
    assertThat(response.content().get().text())
        .contains("Missing explicit tool confirmation decision");
  }

  private static InvocationContext pausedContext(
      final String invocationId,
      final String pauseInvocationId,
      final String pauseReason,
      final String pausePrompt) {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final SessionState sessionState = new SessionState();
    sessionState.markPaused(
        pausePrompt, List.of(), pauseReason, pauseInvocationId, Instant.now().toEpochMilli());
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
    when(context.invocationId()).thenReturn(invocationId);
    when(context.session()).thenReturn(session);
    return context;
  }

  private static CallbackContext callbackContext(final InvocationContext context) {
    final CallbackContext callbackContext = mock(CallbackContext.class);
    when(callbackContext.invocationContext()).thenReturn(context);
    return callbackContext;
  }

  private static Content textContent(final String text) {
    return Content.builder().role("user").parts(List.of(Part.fromText(text))).build();
  }
}
