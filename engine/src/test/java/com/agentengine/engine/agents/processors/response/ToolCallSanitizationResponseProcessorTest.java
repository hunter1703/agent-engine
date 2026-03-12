package com.agentengine.engine.agents.processors.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.BaseAgentState;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class ToolCallSanitizationResponseProcessorTest {

  @Test
  void shouldTrackLastToolCallOnFullResponses() {
    final InvocationContext context = invocationContext("agent-1", "session-1");
    final LlmResponse response =
        LlmResponse.builder()
            .content(
                Content.builder()
                    .role("model")
                    .parts(
                        List.of(
                            Part.builder()
                                .functionCall(
                                    FunctionCall.builder()
                                        .name("search")
                                        .args(Map.of("query", "weather"))
                                        .build())
                                .build()))
                    .build())
            .build();

    final LlmResponse updated =
        ToolCallSanitizationResponseProcessor.INSTANCE
            .processResponse(context, response)
            .blockingGet()
            .updatedResponse();

    assertThat(updated).isSameAs(response);
    assertThat(RunStateUtils.getState(context).lastToolCalls())
        .singleElement()
        .satisfies(
            signature -> {
              assertThat(signature.name()).isEqualTo("search");
              assertThat(signature.args()).containsEntry("query", "weather");
            });
  }

  @Test
  void shouldStripToolPartsFromPartialResponses() {
    final InvocationContext context = invocationContext("agent-1", "session-1");
    final LlmResponse response =
        LlmResponse.builder()
            .partial(true)
            .content(
                Content.builder()
                    .role("model")
                    .parts(
                        List.of(
                            Part.fromText("Working on it"),
                            Part.builder()
                                .functionCall(
                                    FunctionCall.builder()
                                        .name("search")
                                        .args(Map.of("query", "weather"))
                                        .build())
                                .build()))
                    .build())
            .build();

    final LlmResponse updated =
        ToolCallSanitizationResponseProcessor.INSTANCE
            .processResponse(context, response)
            .blockingGet()
            .updatedResponse();

    assertThat(updated.content()).isPresent();
    assertThat(updated.content().orElseThrow().parts().orElseThrow())
        .extracting(part -> part.text().orElse(null))
        .containsExactly("Working on it");
    assertThat(RunStateUtils.getState(context).violations())
        .extracting(violation -> violation.code())
        .contains("partial_tool_calls");
  }

  private static InvocationContext invocationContext(
      final String agentId, final String sessionId) {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final Map<String, BaseAgentState> agentStates = new ConcurrentHashMap<>();
    final Session session =
        Session.builder(sessionId)
            .appName(agentId)
            .userId("default")
            .state(state)
            .events(new ArrayList<>())
            .lastUpdateTime(Instant.now())
            .build();
    final BaseAgent agent = mock(BaseAgent.class);
    when(agent.name()).thenReturn(agentId);

    final InvocationContext context = mock(InvocationContext.class);
    when(context.agent()).thenReturn(agent);
    when(context.session()).thenReturn(session);
    when(context.agentStates()).thenReturn(agentStates);
    return context;
  }
}
