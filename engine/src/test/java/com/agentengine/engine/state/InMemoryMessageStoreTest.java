package com.agentengine.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.MessageStoreMark;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryMessageStoreTest {

  @Test
  void appendMessageAssignsIdsAndReturnsCopies() {
    final InMemoryMessageStore messageStore = new InMemoryMessageStore();
    final String sessionId = "session";
    final String modelId = "model";

    final Message message = Message.user("hello");
    final String messageId = messageStore.appendMessage(sessionId, modelId, message);

    assertThat(messageId).isNotBlank();
    assertThat(message.getId()).isEqualTo(messageId);

    final List<Message> messages = messageStore.getMessages(sessionId, modelId);
    messages.clear();

    assertThat(messageStore.getMessages(sessionId, modelId)).hasSize(1);
  }

  @Test
  void resetRemovesMessagesAfterMark() {
    final InMemoryMessageStore messageStore = new InMemoryMessageStore();
    final String sessionId = "session";
    final String modelId = "model";

    messageStore.appendMessage(sessionId, modelId, Message.user("first"));
    messageStore.appendMessage(sessionId, modelId, Message.user("second"));
    final MessageStoreMark mark = messageStore.mark(sessionId, modelId);

    messageStore.appendMessage(sessionId, modelId, Message.user("third"));
    messageStore.reset(sessionId, modelId, mark);

    final List<Message> messages = messageStore.getMessages(sessionId, modelId);
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0).getContent()).isEqualTo("first");
    assertThat(messages.get(1).getContent()).isEqualTo("second");
  }
}
