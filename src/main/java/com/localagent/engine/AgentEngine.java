package com.localagent.engine;

import com.localagent.engine.message.Message;

public interface AgentEngine {

    Message invoke(String sessionId, Message message);


}
