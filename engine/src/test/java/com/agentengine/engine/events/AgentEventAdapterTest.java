package com.agentengine.engine.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.client.beans.session.ToolExecution;
import com.agentengine.engine.client.beans.session.Message;
import com.agentengine.engine.client.beans.session.Role;
import com.agentengine.engine.client.beans.session.ToolCall;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentEventAdapterTest {

  @Test
  void adapterPublishesToolEvents() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    ToolCall toolCall = new ToolCall("id-1", "echo", Map.of("text", "hi"));
    adapter.onToolPlan("session-1", List.of(toolCall));

    ToolExecution execution = new ToolExecution(toolCall, "ok", "done", Instant.parse("2024-01-01T00:00:00Z"), 12);
    adapter.onToolExecution("session-1", execution);

    assertThat(published).hasSize(2);
    AgentEvent planEvent = published.getFirst();
    assertThat(planEvent.event()).isEqualTo("tool_plan");
    assertThat(planEvent.sessionId()).isEqualTo("session-1");
    assertThat(planEvent.payload()).asList().first().isInstanceOf(Map.class);

    AgentEvent execEvent = published.getLast();
    assertThat(execEvent.event()).isEqualTo("tool_execution");
    assertThat(execEvent.sessionId()).isEqualTo("session-1");
    Map<?, ?> payload = (Map<?, ?>) execEvent.payload();
    assertThat(payload.get("tool_name")).isEqualTo("echo");
    assertThat(payload.get("status")).isEqualTo("ok");
  }

  @Test
  void adapterPublishesLifecycleEvents() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    adapter.onReasoningStart("session-2");
    adapter.onReasoningEnd("session-2", Message.assistant("done"));
    adapter.onToolRepair("session-2", List.of(), List.of());

    assertThat(published).hasSize(3);
    assertThat(published.get(0).event()).isEqualTo("reasoning_start");
    assertThat(published.get(1).event()).isEqualTo("reasoning_end");
    assertThat(published.get(2).event()).isEqualTo("tool_repair");

    Map<?, ?> payload = (Map<?, ?>) published.get(1).payload();
    assertThat(payload.get("responseContent")).isEqualTo("done");
    assertThat(payload.get("responseThoughts")).isNull();
  }

  @Test
  void adapterPublishesReasoningEndWithoutMessage() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    adapter.onReasoningEnd("session-5", null);

    assertThat(published).hasSize(1);
    AgentEvent event = published.getFirst();
    assertThat(event.event()).isEqualTo("reasoning_end");
    Map<?, ?> payload = (Map<?, ?>) event.payload();
    assertThat(payload.get("status")).isEqualTo("end");
  }

  @Test
  void adapterPublishesFinalAnswer() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    Message message = new Message(Role.ASSISTANT, "done", "thoughts", List.of(), List.of());
    adapter.onFinalAnswer("session-3", message);

    assertThat(published).hasSize(1);
    AgentEvent event = published.getFirst();
    assertThat(event.event()).isEqualTo("final_answer");
    assertThat(event.sessionId()).isEqualTo("session-3");
    Map<?, ?> payload = (Map<?, ?>) event.payload();
    assertThat(payload.get("final_answer")).isEqualTo("done");
    assertThat(payload.get("thoughts")).isEqualTo("thoughts");
  }

  @Test
  void adapterPublishesFinalAnswerWithNullThoughts() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    Message message = Message.assistant("done");
    adapter.onFinalAnswer("session-4", message);

    assertThat(published).hasSize(1);
    AgentEvent event = published.getFirst();
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) event.payload();
    assertThat(payload.get("final_answer")).isEqualTo("done");
    assertThat(payload).containsKey("thoughts");
    assertThat(payload.get("thoughts")).isNull();
  }
}
