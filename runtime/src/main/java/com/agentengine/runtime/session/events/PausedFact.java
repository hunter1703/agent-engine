package com.agentengine.runtime.session.events;

public final class PausedFact extends SessionFact {

    /** Non-null for child pauses; null for self pauses. */
    private String sessionId;

    /**
     * For self-pauses: the {@code adk_request_confirmation} wrapper call ID, which is also the
     * {@code confirmationId} the client echoes back. For child pauses: the confirmation ID
     * propagated from the child.
     */
    private String confirmationId;

    public PausedFact() {}

    private PausedFact(final String sessionId, final String confirmationId) {
        this.sessionId = sessionId;
        this.confirmationId = confirmationId;
    }

    public static PausedFact childPaused(final String sessionId, final String confirmationId) {
        return new PausedFact(sessionId, confirmationId);
    }

    public static PausedFact selfPaused(final String confirmationId) {
        return new PausedFact(null, confirmationId);
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
}
