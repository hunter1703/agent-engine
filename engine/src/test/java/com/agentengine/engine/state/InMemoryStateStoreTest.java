package com.agentengine.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Summary;
import com.agentengine.engine.api.beans.session.Message;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryStateStoreTest {

  @Test
  void appendMessageAssignsIdsAndReturnsCopies() {
    InMemoryStateStore sessionStore = new InMemoryStateStore();
    String sessionId = "session";
    String runId = "run-1";

    Message message = Message.user("hello");
    String messageId = sessionStore.appendMessage(sessionId, runId, message);

    assertThat(messageId).isNotBlank();
    assertThat(message.getId()).isEqualTo(messageId);
    assertThat(message.getRunId()).isEqualTo(runId);

    List<Message> messages = sessionStore.getMessages(sessionId);
    messages.clear();

    assertThat(sessionStore.getMessages(sessionId)).hasSize(1);
  }

  @Test
  void updateMessageReplacesExistingMessage() {
    InMemoryStateStore sessionStore = new InMemoryStateStore();
    String sessionId = "session";
    String runId = "run-1";
    Message message = Message.user("hello");
    String messageId = sessionStore.appendMessage(sessionId, runId, message);

    Message updated = Message.user("updated");
    sessionStore.updateMessage(sessionId, messageId, updated);

    List<Message> messages = sessionStore.getMessages(sessionId);
    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst().getId()).isEqualTo(messageId);
    assertThat(messages.getFirst().getContent()).isEqualTo("updated");
  }

  @Test
  void addSummaryStoresSummaryEntries() {
    InMemoryStateStore sessionStore = new InMemoryStateStore();
    sessionStore.addSummary("session", "from", "to", "summary", 123L);

    List<Summary> summaries = sessionStore.getSummaries("session");

    assertThat(summaries).hasSize(1);
    assertThat(summaries.getFirst().content()).isEqualTo("summary");
  }
}
