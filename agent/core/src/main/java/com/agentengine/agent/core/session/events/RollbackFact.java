package com.agentengine.agent.core.session.events;

/**
 * Sentinel journal fact that records a rollback request.
 *
 * <p>All events whose {@code invocationId} matches {@code runId}, and all events after them, are
 * excluded from the effective session history on replay.
 */
public final class RollbackFact extends SessionFact {

    private String runId;

    public RollbackFact() {}

    public RollbackFact(final String runId) {
        this.runId = runId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(final String runId) {
        this.runId = runId;
    }
}
