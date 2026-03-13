package com.agentengine.interfaces.rest.dto;

public class ResumeSessionRequest extends AgentRequest {
  private String decision;

  public ResumeSessionRequest() {
  }

  public ResumeSessionRequest(final String answer) {
    this(answer, null);
  }

  public ResumeSessionRequest(final String answer, final String decision) {
    setMessage(answer);
    this.decision = decision;
  }

  public String getDecision() {
    return decision;
  }

  public void setDecision(final String decision) {
    this.decision = decision;
  }
}
