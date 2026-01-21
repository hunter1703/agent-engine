package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.session.Message;

import java.util.List;

public interface Agent {

  Message invoke(String sessionId, Message message, AgentListener listener);

  List<Message> buildPrompt(String sessionId);
}
