package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.util.common.Utils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.BaseAgentState;
import com.google.adk.agents.InvocationContext;
import com.google.adk.sessions.Session;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class TypedUtilsTest {

  @Test
  void shouldReturnSameInstanceWhenAlreadyTyped() {
    final RunState runState = new RunState();

    final RunState resolved = Utils.toType(runState, RunState.class);

    assertThat(resolved).isSameAs(runState);
  }

  @Test
  void shouldConvertDocumentToTypedObject() {
    final Document raw = new Document("offTopicRetries", 1);

    final RunState resolved = Utils.toType(raw, RunState.class);

    assertThat(resolved).isNotNull();
    assertThat(resolved.incrementOffTopicRetries()).isEqualTo(1);
  }

  @Test
  void shouldReturnNullForUnknownType() {
    final Object raw = "not-a-map";

    final RunState resolved = Utils.toType(raw, RunState.class);

    assertThat(resolved).isNull();
  }

  @Test
  void shouldConvertDocumentToRunStateWithViolations() {
    final Document violation =
        new Document("code", "TEST_VIOLATION")
            .append("message", "violation message")
            .append("correctionMessage", "fix it")
            .append("details", Map.of("k", "v"))
            .append("subViolations", List.of());
    final Document raw = new Document("offTopicRetries", 0).append("violations", List.of(violation));

    final RunState resolved = Utils.toType(raw, RunState.class);

    assertThat(resolved).isNotNull();
    assertThat(resolved.violations()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldCoerceExistingAgentStateMapEntryToRunState() {
    final Map<String, BaseAgentState> agentStates = new HashMap<>();
    ((Map<String, Object>) (Map<?, ?>) agentStates).put("agent-1", Map.of("offTopicRetries", 2));
    final InvocationContext context = mock(InvocationContext.class);
    final BaseAgent agent = mock(BaseAgent.class);
    when(agent.name()).thenReturn("agent-1");
    when(context.agent()).thenReturn(agent);
    when(context.agentStates()).thenReturn(agentStates);
    when(context.session())
        .thenReturn(
            Session.builder("session-1")
                .appName("agent-1")
                .userId("default")
                .state(new ConcurrentHashMap<>())
                .events(new ArrayList<>())
                .lastUpdateTime(Instant.now())
                .build());

    final RunState resolved = RunStateUtils.getState(context);

    assertThat(resolved.incrementOffTopicRetries()).isEqualTo(1);
    assertThat(agentStates.get("agent-1")).isInstanceOf(RunState.class);
  }
}
