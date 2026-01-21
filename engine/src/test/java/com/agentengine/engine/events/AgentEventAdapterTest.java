package com.agentengine.engine.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.api.beans.session.ToolCall;
import java.util.ArrayList;
import java.util.Collection;
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

    adapter.onToolCallResult("session-1", "id-1", "done");

    assertThat(published).hasSize(2);
    AgentEvent planEvent = published.getFirst();
    assertThat(planEvent.event()).isEqualTo("tool_plan");
    assertThat(planEvent.sessionId()).isEqualTo("session-1");

    AgentEvent resultEvent = published.getLast();
    assertThat(resultEvent.event()).isEqualTo("tool_call_result");
    assertThat(resultEvent.sessionId()).isEqualTo("session-1");
    Map<?, ?> payload = (Map<?, ?>) resultEvent.payload();
    assertThat(payload.get("toolCallId")).isEqualTo("id-1");
    assertThat(payload.get("result")).isEqualTo("done");
  }

  @Test
  void adapterPublishesLifecycleEvents() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    adapter.onRunStarted("session-2", "run-123");
    adapter.onStepStarted("session-2", "reasoning");
    adapter.onReasoningMessageEnd("session-2", "msg-1");
    adapter.onStepFinished("session-2", "reasoning");
    adapter.onRunFinished("session-2", "run-123");

    assertThat(published).hasSize(5);
    assertThat(published.get(0).event()).isEqualTo("run_started");
    assertThat(published.get(1).event()).isEqualTo("step_started");
    assertThat(published.get(2).event()).isEqualTo("reasoning_message_end");
    assertThat(published.get(3).event()).isEqualTo("step_finished");
    assertThat(published.get(4).event()).isEqualTo("run_finished");
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
}
