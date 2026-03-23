package com.agentengine.runtime.actor;

import java.util.List;

/** Returns the committed event history for a session, sourced from the actor's persisted state. */
public interface SessionHistory {

    List<SessionEvent> events(String agentId, String sessionId);
}
