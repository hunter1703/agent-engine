package com.agentengine.runtime.api.services;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.ms.MicroService;
import java.util.List;

@MicroService("runtime")
public interface SessionHistoryService {

    List<SessionEvent> getSessionEvents(String sessionId);

    /**
     * Retrieves a unified view of session events including all child session events.
     *
     * <p>Events are sorted by timestamp (chronological order) with event ID as tie-breaker
     * for deterministic ordering when timestamps collide.
     *
     * @param sessionId the root session ID
     * @return unified list of session events from the entire session tree, sorted by timestamp
     */
    List<SessionEvent> getAllSessionEvents(String sessionId);
}
