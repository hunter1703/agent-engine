package com.agentengine.engine.client;

import com.agentengine.engine.client.beans.session.Message;

import java.util.List;

public interface AgentEngine {

  Message invoke(String sessionId, Message message, AgentListener listener);

  List<Message> buildPrompt(String sessionId);
}
