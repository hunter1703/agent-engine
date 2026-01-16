package com.localagent.engine.beans;

import static org.assertj.core.api.Assertions.assertThat;

import com.localagent.engine.message.Message;
import com.localagent.engine.message.Role;
import com.localagent.engine.message.ToolCall;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolExecutionTest {

  @Test
  void toMessageFormatsToolDetails() {
    ToolExecution execution =
        new ToolExecution(
            new ToolCall("id", "echo", Map.of("text", "hi")), "ok", "hi", Instant.now(), 3L);
    execution.setId("exec-1");

    Message message = execution.toMessage();

    assertThat(message.getRole()).isEqualTo(Role.TOOL);
    assertThat(message.getContent()).contains("Tool Id : exec-1");
    assertThat(message.getContent()).contains("Tool call : echo");
    assertThat(message.getContent()).contains("Tool Status : ok");
    assertThat(message.getContent()).contains("Tool output : hi");
  }
}
