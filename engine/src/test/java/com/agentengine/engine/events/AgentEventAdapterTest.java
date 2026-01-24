package com.agentengine.engine.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentEventAdapterTest {

  @Test
  void adapterPublishesRunLifecycleEvents() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    adapter.onRunStarted("session-1", "run-1");
    adapter.onRunFinished("session-1", "run-1");

    assertThat(published).hasSize(2);
    assertThat(published.get(0).event()).isEqualTo("run_started");
    assertThat(published.get(1).event()).isEqualTo("run_finished");

    Map<?, ?> startPayload = (Map<?, ?>) published.get(0).payload();
    assertThat(startPayload.get("runId")).isEqualTo("run-1");
  }

  @Test
  void adapterPublishesStepLifecycleEvents() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    adapter.onStepStarted("session-1", "step-1");
    adapter.onStepFinished("session-1", "step-1");

    assertThat(published).hasSize(2);
    assertThat(published.get(0).event()).isEqualTo("step_started");
    assertThat(published.get(1).event()).isEqualTo("step_finished");

    Map<?, ?> startPayload = (Map<?, ?>) published.get(0).payload();
    assertThat(startPayload.get("stepName")).isEqualTo("step-1");
  }

  @Test
  void adapterPublishesTextMessageEvents() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    adapter.onTextMessageStart("session-1", "msg-1", "assistant");
    adapter.onTextMessageDelta("session-1", "msg-1", "hello");
    adapter.onTextMessageEnd("session-1", "msg-1");

    assertThat(published).hasSize(3);
    assertThat(published.get(0).event()).isEqualTo("text_message_start");
    assertThat(published.get(1).event()).isEqualTo("text_message_delta");
    assertThat(published.get(2).event()).isEqualTo("text_message_end");

    Map<?, ?> deltaPayload = (Map<?, ?>) published.get(1).payload();
    assertThat(deltaPayload.get("delta")).isEqualTo("hello");
  }

  @Test
  void adapterPublishesThinkingMessageEvents() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    adapter.onThinkingMessageStart("session-1", "msg-1", "assistant");
    adapter.onThinkingMessageDelta("session-1", "msg-1", "thinking...");
    adapter.onThinkingMessageEnd("session-1", "msg-1");

    assertThat(published).hasSize(3);
    assertThat(published.get(0).event()).isEqualTo("thinking_message_start");
    assertThat(published.get(1).event()).isEqualTo("thinking_message_delta");
    assertThat(published.get(2).event()).isEqualTo("thinking_message_end");

    Map<?, ?> deltaPayload = (Map<?, ?>) published.get(1).payload();
    assertThat(deltaPayload.get("delta")).isEqualTo("thinking...");
  }

  @Test
  void adapterPublishesToolCallEvents() {
    List<AgentEvent> published = new ArrayList<>();
    AgentEventAdapter adapter = new AgentEventAdapter(published::add);

    adapter.onToolCallStart("session-1", "call-1", "echo");
    adapter.onToolCallArgs("session-1", "call-1", "{\"text\":");
    adapter.onToolCallArgs("session-1", "call-1", "\"hi\"}");
    adapter.onToolCallEnd("session-1", "call-1");
    adapter.onToolCallResult("session-1", "call-1", "hi");

    assertThat(published).hasSize(5);
    assertThat(published.get(0).event()).isEqualTo("tool_call_start");
    assertThat(published.get(1).event()).isEqualTo("tool_call_args");
    assertThat(published.get(3).event()).isEqualTo("tool_call_end");
    assertThat(published.get(4).event()).isEqualTo("tool_call_result");

    Map<?, ?> resultPayload = (Map<?, ?>) published.get(4).payload();
    assertThat(resultPayload.get("result")).isEqualTo("hi");
  }

}
