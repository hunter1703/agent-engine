package com.agentengine.runtime.session.events;

public final class PausedFact extends SessionFact {

    /** Non-null for child pauses; null for self pauses. */
    private String sessionId;
    /**
     * For self-pauses: the {@code adk_request_confirmation} wrapper call ID, which is also the
     * {@code confirmationId} the client echoes back. For child pauses: the confirmation ID
     * propagated from the child.
     */
    private String correlationId;

    private String confirmationId;
    private boolean internal;

    public PausedFact() {}

    private PausedFact(
            final String sessionId, final String correlationId, final String confirmationId, final boolean internal) {
        this.sessionId = sessionId;
        this.correlationId = correlationId;
        this.confirmationId = confirmationId;
        this.internal = internal;
    }

    public static PausedFact childPaused(final String sessionId, final String confirmationId) {
        return new PausedFact(sessionId, null, confirmationId, false);
    }

    public static PausedFact externalSelfPaused(final String confirmationId) {
        return new PausedFact(null, null, confirmationId, false);
    }

    public static PausedFact internalSelfPause(final String correlationId, final String confirmationId) {
        return new PausedFact(null, correlationId, confirmationId, true);
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

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(final boolean internal) {
        this.internal = internal;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(final String correlationId) {
        this.correlationId = correlationId;
    }
}
