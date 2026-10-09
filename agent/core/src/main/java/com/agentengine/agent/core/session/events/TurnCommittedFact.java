package com.agentengine.agent.core.session.events;

import java.util.List;

public final class TurnCommittedFact extends SessionFact {

  private String runId;
  private int turnId;
  private String lastEventId;
  private int eventCount;
  private List<String> deliveredResumeIds;

  public TurnCommittedFact() {}

  public TurnCommittedFact(
      final String runId,
      final int turnId,
      final String lastEventId,
      final int eventCount,
      final List<String> deliveredResumeIds) {
    this.runId = runId;
    this.turnId = turnId;
    this.lastEventId = lastEventId;
    this.eventCount = eventCount;
    this.deliveredResumeIds = deliveredResumeIds;
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

  /** The interrupt ids of the resumes this turn handed to the model. */
  public List<String> getDeliveredResumeIds() {
    return deliveredResumeIds;
  }

  public void setDeliveredResumeIds(final List<String> deliveredResumeIds) {
    this.deliveredResumeIds = deliveredResumeIds;
  }
}
