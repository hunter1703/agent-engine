package com.agentengine.runtime.actor;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.ms.MicroService;

import java.util.List;

/** Returns the committed event history for a session from the projection store. */
@MicroService("runtime")
public interface SessionHistoryService {

    List<SessionEvent> events(String sessionId);
}
