package com.agentengine.agent.core.session.events;

public final class ChildStartFailedFact extends SessionFact {

    private String sessionId;

    public ChildStartFailedFact() {}

    public ChildStartFailedFact(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }
}
