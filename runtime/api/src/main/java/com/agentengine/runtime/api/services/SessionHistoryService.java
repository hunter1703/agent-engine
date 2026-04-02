package com.agentengine.runtime.api.services;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.ms.MicroService;
import java.util.List;

@MicroService("runtime")
public interface SessionHistoryService {

    List<SessionEvent> getSessionEvents(String sessionId);
}
