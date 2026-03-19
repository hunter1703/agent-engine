package com.agentengine.engine.agents.processors.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.BaseAgentState;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class RunCompletionResponseProcessorTest {
  private static final RunCompletionResponseProcessor PROCESSOR = new RunCompletionResponseProcessor(null);

  @Test
  void shouldConsumeContinuationOnNonPartialNonFinalResponses() {
    final InvocationContext context = invocationContext("agent-1", "session-1");
    RunUtils.getOrInitState(context).requestContinuation();
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().role("model")
            .parts(java.util.List
                .of(Part.builder().functionCall(FunctionCall.builder().name("search").args(Map.of("query", "weather")).build()).build()))
            .build())
        .build();

    final LlmResponse updated = PROCESSOR.processResponse(context, response).blockingGet().updatedResponse();

    assertThat(updated.turnComplete().orElse(false)).isTrue();
    assertThat(updated.finishReason()).isEmpty();
    assertThat(RunUtils.getOrInitState(context).hasContinuationRequested()).isFalse();
  }

  @Test
  void shouldSetStopFinishReasonOnFinalAnswerWhenContinuationNotRequested() {
    final InvocationContext context = invocationContext("agent-1", "session-1");
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().role("model").parts(java.util.List.of(Part.fromText("done"))).build()).build();

    final LlmResponse updated = PROCESSOR.processResponse(context, response).blockingGet().updatedResponse();

    assertThat(updated.turnComplete().orElse(false)).isTrue();
    assertThat(updated.finishReason()).isPresent();
  }

  @Test
  void shouldHonorMaxStepsWhenRunStateIsRebuiltFromPriorToolTurns() {
    final FunctionCall priorCall = FunctionCall.builder().name("search").args(Map.of("query", "weather")).build();
    final Event priorTurn = Event.builder()
        .content(Content.builder().role("model").parts(java.util.List.of(Part.builder().functionCall(priorCall).build())).build()).build();
    final InvocationContext context = invocationContext("agent-1", "session-1", java.util.List.of(priorTurn), false);
    final RunCompletionResponseProcessor processor = new RunCompletionResponseProcessor(2);
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().role("model")
            .parts(java.util.List
                .of(Part.builder().functionCall(FunctionCall.builder().name("search").args(Map.of("query", "forecast")).build()).build()))
            .build())
        .build();

    final LlmResponse updated = processor.processResponse(context, response).blockingGet().updatedResponse();

    assertThat(updated.turnComplete().orElse(false)).isTrue();
    assertThat(updated.finishReason()).isPresent();
  }

  @Test
  void shouldConsumeMaxStepsAcrossContinuationDrivenFinalAnswerRetries() {
    final InvocationContext context = invocationContext("agent-1", "session-1");
    final RunCompletionResponseProcessor processor = new RunCompletionResponseProcessor(2);
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().role("model").parts(java.util.List.of(Part.fromText("Let me try again."))).build()).build();

    RunUtils.getOrInitState(context).requestContinuation();
    final LlmResponse first = processor.processResponse(context, response).blockingGet().updatedResponse();

    RunUtils.getOrInitState(context).requestContinuation();
    final LlmResponse second = processor.processResponse(context, response).blockingGet().updatedResponse();

    assertThat(first.turnComplete().orElse(false)).isTrue();
    assertThat(first.finishReason()).isEmpty();
    assertThat(second.turnComplete().orElse(false)).isTrue();
    assertThat(second.finishReason()).isPresent();
  }

  private static InvocationContext invocationContext(final String agentId, final String sessionId) {
    return invocationContext(agentId, sessionId, new ArrayList<>(), true);
  }

  private static InvocationContext invocationContext(final String agentId, final String sessionId, final java.util.List<Event> events,
      final boolean seedRunState) {
    final Session session = Session.builder(sessionId).appName(agentId).userId("default").state(new ConcurrentHashMap<>())
        .events(new ArrayList<>(events)).lastUpdateTime(Instant.now()).build();
    final BaseAgent agent = mock(BaseAgent.class);
    when(agent.name()).thenReturn(agentId);

    final Map<String, BaseAgentState> agentStates = new ConcurrentHashMap<>();
    if (seedRunState) {
      agentStates.put(agentId, new RunState());
    }

    final InvocationContext context = mock(InvocationContext.class);
    when(context.agent()).thenReturn(agent);
    when(context.session()).thenReturn(session);
    when(context.agentStates()).thenReturn(agentStates);
    return context;
  }
}
