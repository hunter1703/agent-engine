package com.agentengine.agent.core.session.events;

public final class TurnCommittedFact extends SessionFact {

  private String runId;
  private int turnId;
  private String lastEventId;
  private int eventCount;

  public TurnCommittedFact() {}

  public TurnCommittedFact(
      final String runId, final int turnId, final String lastEventId, final int eventCount) {
    this.runId = runId;
    this.turnId = turnId;
    this.lastEventId = lastEventId;
    this.eventCount = eventCount;
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

  public String getLastEventId() {
    return lastEventId;
  }

  public void setLastEventId(final String lastEventId) {
    this.lastEventId = lastEventId;
  }

  public int getEventCount() {
    return eventCount;
  }

  public void setEventCount(final int eventCount) {
    this.eventCount = eventCount;
  }
}
