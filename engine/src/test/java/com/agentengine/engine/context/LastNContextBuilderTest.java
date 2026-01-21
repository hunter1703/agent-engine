package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.state.InMemorySessionStore;
import java.util.List;

import org.junit.jupiter.api.Test;

class LastNContextBuilderTest {

  @Test
  void buildPromptIncludesRecentMessages() {
    InMemorySessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    Message first = Message.user("first");
    Message second = Message.user("second");
    sessionStore.appendMessage(sessionId, first);
    sessionStore.appendMessage(sessionId, second);

    LastNContextBuilder builder = new LastNContextBuilder(sessionStore, "system", "protocol", List.of(), 2);

    List<Message> prompt = builder.buildPrompt(sessionId);

    assertThat(prompt).hasSize(4);
    assertThat(prompt.get(0).getContent()).isEqualTo("protocol");
    assertThat(prompt.get(1).getContent()).isEqualTo("system");
    assertThat(prompt.get(2).getContent()).isEqualTo("first");
    assertThat(prompt.get(3).getContent()).isEqualTo("second");
  }
}
