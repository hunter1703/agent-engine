package com.agentengine.agent.core.session.events;

/** Marks that {@code runId}'s completion side effects ran to completion. */
public final class RunSettledFact extends SessionFact {

  private String runId;

  public RunSettledFact() {
    this(null);
  }

  public RunSettledFact(final String runId) {
    this.runId = runId;
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(final String runId) {
    this.runId = runId;
  }
}
