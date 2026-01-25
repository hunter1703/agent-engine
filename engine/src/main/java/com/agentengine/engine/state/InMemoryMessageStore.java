package com.agentengine.engine.state;

import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.MessageStoreMark;
import com.agentengine.engine.api.beans.session.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class InMemoryMessageStore implements MessageStore {
  private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

  @Override
  public List<Message> getMessages(final String sessionId, final String modelId) {
    final ModelState modelState = modelState(sessionId, modelId);
    List<Message> messages = new ArrayList<>();
    for (String messageId : modelState.messageOrder) {
      Message message = modelState.messagesById.get(messageId);
      if (message != null) {
        messages.add(message);
      }
    }
    return messages;
  }

  @Override
  public String appendMessage(final String sessionId, final String modelId, final Message message) {
    message.setId(UUID.randomUUID().toString().replaceAll("-", ""));
    final ModelState modelState = modelState(sessionId, modelId);
    modelState.messagesById.put(message.getId(), message);
    modelState.messageOrder.add(message.getId());
    return message.getId();
  }

  @Override
  public MessageStoreMark mark(final String sessionId, final String modelId) {
    final ModelState modelState = modelState(sessionId, modelId);
    return new MessageStoreMark(modelState.messageOrder.size());
  }

  @Override
  public void reset(final String sessionId, final String modelId, final MessageStoreMark mark) {
    if (mark == null) {
      return;
    }
    final ModelState modelState = modelState(sessionId, modelId);
    final List<String> ordered = new ArrayList<>(modelState.messageOrder);
    final int keep = Math.max(0, mark.messageCount());
    if (keep >= ordered.size()) {
      return;
    }
    modelState.messageOrder.clear();
    for (int index = 0; index < ordered.size(); index++) {
      final String messageId = ordered.get(index);
      if (index < keep) {
        modelState.messageOrder.add(messageId);
      } else {
        modelState.messagesById.remove(messageId);
      }
    }
  }

  private ModelState modelState(final String sessionId, final String modelId) {
    final SessionState sessionState = sessions.computeIfAbsent(sessionId, _ -> new SessionState());
    return sessionState.modelStates.computeIfAbsent(modelId, _ -> new ModelState());
  }

  private static final class SessionState {
    private final Map<String, ModelState> modelStates = new ConcurrentHashMap<>();
  }

  private static final class ModelState {
    private final Map<String, Message> messagesById = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> messageOrder = new ConcurrentLinkedQueue<>();
  }
}
