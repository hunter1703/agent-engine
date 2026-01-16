package com.localagent.engine;

import com.localagent.engine.message.Message;

import java.util.List;

public interface AgentEngine {

    Message invoke(String sessionId, Message message);

    void registerListener(AgentListener listener);

    List<Message> buildPrompt(String sessionId);

}
