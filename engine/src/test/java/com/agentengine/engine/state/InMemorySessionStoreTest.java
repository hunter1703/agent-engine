package com.agentengine.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Summary;
import com.agentengine.engine.api.beans.session.Message;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemorySessionStoreTest {

  @Test
  void appendMessageAssignsIdsAndReturnsCopies() {
    InMemorySessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    Message message = Message.user("hello");
    String messageId = sessionStore.appendMessage(sessionId, message);

    assertThat(messageId).isNotBlank();
    assertThat(message.getId()).isEqualTo(messageId);

    List<Message> messages = sessionStore.getMessages(sessionId);
    messages.clear();

    assertThat(sessionStore.getMessages(sessionId)).hasSize(1);
  }

  @Test
  void updateMessageReplacesExistingMessage() {
    InMemorySessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";
    Message message = Message.user("hello");
    String messageId = sessionStore.appendMessage(sessionId, message);

    Message updated = Message.user("updated");
    sessionStore.updateMessage(sessionId, messageId, updated);

    List<Message> messages = sessionStore.getMessages(sessionId);
    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst().getId()).isEqualTo(messageId);
    assertThat(messages.getFirst().getContent()).isEqualTo("updated");
  }

  @Test
  void addSummaryStoresSummaryEntries() {
    InMemorySessionStore sessionStore = new InMemorySessionStore();
    sessionStore.addSummary("session", "from", "to", "summary", 123L);

    List<Summary> summaries = sessionStore.getSummaries("session");

    assertThat(summaries).hasSize(1);
    assertThat(summaries.getFirst().content()).isEqualTo("summary");
  }
}
