package com.agentengine.engine.context;

import com.agentengine.engine.message.Message;
import com.agentengine.engine.state.SessionStore;
import com.agentengine.engine.tools.Tool;
import java.util.ArrayList;
import java.util.List;

public final class LastNContextBuilder extends BaseContextBuilder {
  private final int keepLast;

  public LastNContextBuilder(final SessionStore sessionStore, final String systemMessage, final String protocolMessage,
      final List<Tool> tools, final int keepLast) {
    super(sessionStore, systemMessage, protocolMessage, tools);
    this.keepLast = Math.max(1, keepLast);
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    final List<Message> messages = sessionStore.getMessages(sessionId);
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
