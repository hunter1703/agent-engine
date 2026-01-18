package com.agentengine.engine.events;

public record AgentEvent(String event, String sessionId, Object payload, long createdTime) {
    public AgentEvent(String event, String sessionId, Object payload) {
        this(event, sessionId, payload, System.currentTimeMillis());
    }
}
