package com.agentengine.engine.context;

import com.agentengine.engine.api.beans.session.Message;

import java.util.List;

public interface ContextBuilder {
  List<Message> buildPrompt(String sessionId);
}
