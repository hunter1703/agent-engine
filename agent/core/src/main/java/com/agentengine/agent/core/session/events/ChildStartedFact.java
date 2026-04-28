package com.agentengine.agent.core.session.events;

public final class ChildStartedFact extends SessionFact {

    private String sessionId;
    private String agentId;

    public ChildStartedFact() {}

    public ChildStartedFact(final String sessionId, final String agentId) {
        this.sessionId = sessionId;
        this.agentId = agentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(final String agentId) {
        this.agentId = agentId;
    }
}
