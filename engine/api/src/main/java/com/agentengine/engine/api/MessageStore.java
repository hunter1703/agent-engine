package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.session.Message;

import java.util.List;

public interface MessageStore {

  /**
   * @param sessionId unique globally
   * @param modelId unique within a given session only
   * @return
   */
  List<Message> getMessages(String sessionId, String modelId);

  String appendMessage(String sessionId, String modelId, Message message);

  MessageStoreMark mark(String sessionId, String modelId);

  void reset(String sessionId, String modelId, MessageStoreMark mark);
}
