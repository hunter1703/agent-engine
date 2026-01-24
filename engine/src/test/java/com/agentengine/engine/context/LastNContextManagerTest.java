package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.state.InMemoryStateStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class LastNContextManagerTest {

  @Test
  void buildPromptIncludesRecentMessages() {
    InMemoryStateStore sessionStore = new InMemoryStateStore();
    String sessionId = "session";

    sessionStore.appendMessage(sessionId, "run", Message.user("first"));
    sessionStore.appendMessage(sessionId, "run", Message.user("second"));

    LastNContextManager builder = new LastNContextManager(sessionStore, "system", "protocol", List.of(), 1);

    List<Message> prompt = builder.buildPrompt(sessionId);

    assertThat(prompt).hasSize(2);
    assertThat(prompt.getFirst().getContent()).contains("system");
    assertThat(prompt.get(1).getContent()).isEqualTo("second");
  }
}
