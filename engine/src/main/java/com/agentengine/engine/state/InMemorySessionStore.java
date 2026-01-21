package com.agentengine.engine.state;

import com.agentengine.engine.api.beans.session.Summary;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.state.SessionStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemorySessionStore implements SessionStore {
  private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

  @Override
  public List<Message> getMessages(final String sessionId) {
    SessionState session = session(sessionId);
    List<Message> messages = new ArrayList<>();
    for (String messageId : session.messageOrder) {
      Message message = session.messagesById.get(messageId);
      if (message != null) {
        messages.add(message);
      }
    }
    return messages;
  }

  @Override
  public String appendMessage(final String sessionId, final Message message) {
    message.setId(UUID.randomUUID().toString().replaceAll("-", ""));
    final SessionState session = session(sessionId);
    session.messagesById.put(message.getId(), message);
    session.messageOrder.add(message.getId());
    return message.getId();
  }

  @Override
  public void updateMessage(final String sessionId, final String messageId, final Message message) {
    final SessionState session = session(sessionId);
    if (!session.messagesById.containsKey(messageId)) {
      return;
    }
    message.setId(messageId);
    session.messagesById.put(messageId, message);
  }

  @Override
  public List<Summary> getSummaries(final String sessionId) {
    return new ArrayList<>(session(sessionId).summaries);
  }

  @Override
  public void addSummary(final String sessionId, final String summarizedFromMessageId,
      final String summarizedUptoMessageId, final String summary, final long createdAt) {
    session(sessionId).summaries.add(new Summary(UUID.randomUUID().toString().replaceAll("-", ""), summary,
        summarizedFromMessageId, summarizedUptoMessageId, createdAt));
  }

  private SessionState session(final String sessionId) {
    return sessions.computeIfAbsent(sessionId, _ -> new SessionState());
  }

  private static final class SessionState {
    private final ConcurrentMap<String, Message> messagesById = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> messageOrder = new ConcurrentLinkedQueue<>();
    private final List<Summary> summaries = new CopyOnWriteArrayList<>();
  }
}
