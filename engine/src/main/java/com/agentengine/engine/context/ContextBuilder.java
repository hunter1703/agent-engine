package com.agentengine.engine.context;

import com.agentengine.engine.message.Message;
import java.util.List;

public interface ContextBuilder {
  List<Message> buildPrompt(String sessionId);
}
