package com.agentengine.runtime.actor;

import java.util.List;

/** Returns the committed event history for a session from the projection store. */
public interface SessionHistory {

    List<SessionEvent> events(String sessionId);
}
