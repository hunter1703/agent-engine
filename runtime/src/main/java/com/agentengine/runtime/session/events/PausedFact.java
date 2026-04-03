package com.agentengine.runtime.session.events;

public final class PausedFact extends SessionFact {

    /** Non-null for child pauses; null for self pauses. */
    private String sessionId;

    private String confirmationId;

    /**
     * ADK wrapper call ID ({@code adk_request_confirmation}) paired with this pause. Non-null for
     * self pauses (both IDs are known at the moment we detect the pause from the event stream);
     * null for child pauses.
     */
    private String wrapperCallId;

    public PausedFact() {}

    private PausedFact(final String sessionId, final String confirmationId, final String wrapperCallId) {
        this.sessionId = sessionId;
        this.confirmationId = confirmationId;
        this.wrapperCallId = wrapperCallId;
    }

    public static PausedFact childPaused(final String sessionId, final String confirmationId) {
        return new PausedFact(sessionId, confirmationId, null);
    }

    public static PausedFact selfPaused(final String confirmationId, final String wrapperCallId) {
        return new PausedFact(null, confirmationId, wrapperCallId);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(final String confirmationId) {
        this.confirmationId = confirmationId;
    }

    public String getWrapperCallId() {
        return wrapperCallId;
    }

    public void setWrapperCallId(final String wrapperCallId) {
        this.wrapperCallId = wrapperCallId;
    }
}
