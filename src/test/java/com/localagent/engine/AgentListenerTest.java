package com.localagent.engine;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.localagent.engine.beans.ToolExecution;
import com.localagent.engine.message.ToolCall;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentListenerTest {

  @Test
  void defaultHooksAreNoOps() {
    AgentListener listener = new AgentListener() {};

    assertThatCode(
            () -> listener.onToolPlan("session", List.of(new ToolCall("id", "tool", Map.of()))))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                listener.onToolExecution(
                    "session",
                    new ToolExecution(
                        new ToolCall("id", "tool", Map.of()), "ok", "out", Instant.now(), 1)))
        .doesNotThrowAnyException();
    assertThatCode(() -> listener.onReasoningStart("session")).doesNotThrowAnyException();
    assertThatCode(() -> listener.onReasoningEnd("session")).doesNotThrowAnyException();
    assertThatCode(() -> listener.onToolRepair("session")).doesNotThrowAnyException();
  }
}
