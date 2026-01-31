package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.state.InMemoryMessageStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class LastNContextManagerTest {

  @Test
  void buildPromptIncludesRecentMessages() {
    final InMemoryMessageStore messageStore = new InMemoryMessageStore();
    final String sessionId = "session";
    final String role = "reasoning";

    messageStore.appendMessage(sessionId, role, Message.user("first"));
    messageStore.appendMessage(sessionId, role, Message.user("second"));

    final LastNContextManager builder = new LastNContextManager(role, messageStore, "system", "protocol", List.of(), 1);

    final List<Message> prompt = builder.buildPrompt(sessionId);

    assertThat(prompt).hasSize(2);
    assertThat(prompt.getFirst().getContent()).contains("system");
    assertThat(prompt.get(1).getContent()).isEqualTo("second");
  }
}
