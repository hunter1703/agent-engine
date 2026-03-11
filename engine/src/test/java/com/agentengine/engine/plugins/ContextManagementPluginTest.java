package com.agentengine.engine.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.ContextManager;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class ContextManagementPluginTest {

  @Test
  void shouldReplaceRequestContentsFromContextManager() {
    final LlmRequest.Builder requestBuilder =
        LlmRequest.builder().contents(List.of(textContent("original")));
    final ContextManager contextManager =
        (agentId, sessionId, contents) -> List.of(textContent(agentId + ":" + sessionId));

    final ContextManagementPlugin plugin =
        new ContextManagementPlugin(Map.of("agent-1", contextManager));

    plugin
        .beforeModelCallback(callbackContext("agent-1", "session-1"), requestBuilder)
        .blockingGet();
    final String text = requestBuilder.build().contents().getFirst().text();
    assertThat(text).isEqualTo("agent-1:session-1");
  }

  @Test
  void shouldKeepOriginalContentsWhenContextManagerFails() {
    final LlmRequest.Builder requestBuilder =
        LlmRequest.builder().contents(List.of(textContent("original")));
    final ContextManager contextManager =
        (agentId, sessionId, contents) -> {
          throw new IllegalStateException("boom");
        };

    final ContextManagementPlugin plugin =
        new ContextManagementPlugin(Map.of("agent-1", contextManager));

    plugin
        .beforeModelCallback(callbackContext("agent-1", "session-1"), requestBuilder)
        .blockingGet();
    final String text = requestBuilder.build().contents().getFirst().text();
    assertThat(text).isEqualTo("original");
  }

  private static CallbackContext callbackContext(final String agentId, final String sessionId) {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final Session session =
        Session.builder(sessionId)
            .appName(agentId)
            .userId("default")
            .state(state)
            .events(new ArrayList<>())
            .lastUpdateTime(Instant.now())
            .build();
    final BaseAgent invocationAgent = mock(BaseAgent.class);
    when(invocationAgent.name()).thenReturn(agentId);

    final InvocationContext invocationContext = mock(InvocationContext.class);
    when(invocationContext.agent()).thenReturn(invocationAgent);
    when(invocationContext.session()).thenReturn(session);

    final CallbackContext callbackContext = mock(CallbackContext.class);
    when(callbackContext.invocationContext()).thenReturn(invocationContext);
    return callbackContext;
  }

  private static Content textContent(final String text) {
    return Content.builder().role("user").parts(List.of(Part.fromText(text))).build();
  }
}
