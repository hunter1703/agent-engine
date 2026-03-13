package com.agentengine.engine.events;

/** Fired after a session has been deleted from the repository. */
public record SessionDeletedEvent(String sessionId) {
}
