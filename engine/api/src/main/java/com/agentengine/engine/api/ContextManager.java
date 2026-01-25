package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.session.Message;

import java.util.List;

public interface ContextManager {

  List<Message> buildPrompt(String sessionId);

  String appendMessage(String sessionId, String runId, Message message);

  MessageStoreMark mark(String sessionId);

  void reset(String sessionId, MessageStoreMark mark);
}
