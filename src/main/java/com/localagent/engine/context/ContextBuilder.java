package com.localagent.engine.context;

import com.localagent.engine.message.Message;
import com.localagent.engine.state.SessionStore;

import java.util.List;

public interface ContextBuilder {
    List<Message> buildPrompt(String sessionId);
}
