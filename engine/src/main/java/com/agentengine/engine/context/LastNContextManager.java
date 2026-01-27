package com.agentengine.engine.context;

import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.Tool;
import java.util.ArrayList;
import java.util.List;

public final class LastNContextManager extends BaseContextManager {
  private final int keepLast;

  public LastNContextManager(final String role, final MessageStore messageStore, final String systemMessage,
      final String protocolMessage, final List<Tool> tools, final int keepLast) {
    super(role, messageStore, systemMessage, protocolMessage, tools);
    this.keepLast = Math.max(1, keepLast);
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    final List<Message> messages = messageStore.getMessages(sessionId, role);
    return super.buildPrompt(selectRecentMessages(messages));
  }

  private List<Message> selectRecentMessages(final List<Message> messages) {
    final List<Message> recent = new ArrayList<>();
    int count = 0;
    for (int i = messages.size() - 1; i >= 0; i--) {
      final Message message = messages.get(i);
      recent.add(message);
      if (++count == keepLast) {
        break;
      }
    }
    return recent.reversed();
  }
}
