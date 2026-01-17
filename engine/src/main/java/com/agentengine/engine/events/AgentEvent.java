package com.agentengine.engine.events;

public record AgentEvent(String event, String sessionId, Object payload) {
}
