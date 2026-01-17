package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.Role;
import com.agentengine.engine.message.ToolCall;
import com.agentengine.engine.state.InMemorySessionStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LastNContextBuilderTest {

  @Test
  void buildPromptIncludesToolExecutionsForRecentMessages() {
    InMemorySessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    Message first = Message.user("first");
    Message second = Message.user("second");
    sessionStore.appendMessage(sessionId, first);
    sessionStore.appendMessage(sessionId, second);

    ToolExecution execution =
        new ToolExecution(
            new ToolCall("t1", "echo", Map.of("text", "hi")), "ok", "hi", Instant.now(), 5L);
    sessionStore.addToolExecutions(sessionId, second.getId(), List.of(execution));

    LastNContextBuilder builder =
        new LastNContextBuilder(sessionStore, "system", "protocol", List.of(), 1);

    List<Message> prompt = builder.buildPrompt(sessionId);

    assertThat(prompt).hasSize(4);
    assertThat(prompt.get(0).getContent()).isEqualTo("protocol");
    assertThat(prompt.get(1).getContent()).isEqualTo("system");
    assertThat(prompt.get(2).getContent()).isEqualTo("second");
    assertThat(prompt.get(3).getRole()).isEqualTo(Role.TOOL);
  }
}

