package com.agentengine.engine.client;

import com.agentengine.engine.client.beans.session.Message;

import java.util.List;

public interface AgentEngine {

  Message invoke(String sessionId, Message message);

  void registerListener(String sessionId, AgentListener listener);

  void unRegisterListener(String sessionId);

  List<Message> buildPrompt(String sessionId);
}
