package com.agentengine.runtime.actor;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.ms.MicroService;
import java.util.List;

/** Returns the committed event history for a session directly from the journal. */
@MicroService("runtime")
public interface SessionHistoryService {

    List<SessionEvent> getSessionEvents(String sessionId);
}
