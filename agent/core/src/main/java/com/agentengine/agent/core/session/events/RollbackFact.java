package com.agentengine.agent.core.session.events;

/** Sentinel journal fact recording that a rollback command was accepted for {@code runId}. */
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
