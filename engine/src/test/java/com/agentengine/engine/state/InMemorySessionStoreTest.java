package com.agentengine.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.beans.Summary;
import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.ToolCall;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
  void addToolExecutionsAssignsIdsAndRetrievesByMessageId() {
    InMemorySessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";
    Message message = Message.user("hello");
    sessionStore.appendMessage(sessionId, message);

    ToolExecution execution = new ToolExecution(new ToolCall("call", "echo", Map.of()), "ok", "done", Instant.now(),
        10L);
    sessionStore.addToolExecutions(sessionId, message.getId(), List.of(execution));

    assertThat(execution.getId()).isNotBlank();

    Map<String, List<ToolExecution>> executions = sessionStore.getToolExecutions(sessionId, List.of(message.getId()));

    assertThat(executions.get(message.getId())).hasSize(1);
    assertThat(executions.get(message.getId()).getFirst().getOutput()).isEqualTo("done");
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
