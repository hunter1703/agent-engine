package com.agentengine.engine;

import com.agentengine.engine.message.Message;
import java.util.List;

public interface AgentEngine {

  Message invoke(String sessionId, Message message);

  void registerListener(AgentListener listener);

  List<Message> buildPrompt(String sessionId);
}
