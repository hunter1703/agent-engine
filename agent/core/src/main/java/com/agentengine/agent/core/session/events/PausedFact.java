package com.agentengine.agent.core.session.events;

public final class PausedFact extends SessionFact {

  /** The child session this pause concerns */
  private String childSessionId;

  private String interruptId;
  private boolean internal;
  private String runId;
  private int turnId;

  public PausedFact() {}

  private PausedFact(
      final String childSessionId,
      final String interruptId,
      final boolean internal,
      final String runId,
      final int turnId) {
    this.childSessionId = childSessionId;
    this.interruptId = interruptId;
    this.internal = internal;
    this.runId = runId;
    this.turnId = turnId;
  }

  public static PausedFact childPaused(final String childSessionId, final String interruptId) {
    return new PausedFact(childSessionId, interruptId, false, null, 0);
  }

  public static PausedFact externalSelfPaused(
      final String interruptId, final String runId, final int turnId) {
    return new PausedFact(null, interruptId, false, runId, turnId);
  }

  public static PausedFact internalSelfPause(
      final String childSessionId, final String interruptId, final String runId, final int turnId) {
    return new PausedFact(childSessionId, interruptId, true, runId, turnId);
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(final String runId) {
    this.runId = runId;
  }

  public int getTurnId() {
    return turnId;
  }

  public void setTurnId(final int turnId) {
    this.turnId = turnId;
  }

  public String getChildSessionId() {
    return childSessionId;
  }

  public void setChildSessionId(final String childSessionId) {
    this.childSessionId = childSessionId;
  }

  public String getInterruptId() {
    return interruptId;
  }

  public void setInterruptId(final String interruptId) {
    this.interruptId = interruptId;
  }

  public boolean isInternal() {
    return internal;
  }

  public void setInternal(final boolean internal) {
    this.internal = internal;
  }
}
