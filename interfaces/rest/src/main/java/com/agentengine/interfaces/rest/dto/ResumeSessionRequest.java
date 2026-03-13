package com.agentengine.interfaces.rest.dto;

public class ResumeSessionRequest extends AgentRequest {
  private Boolean confirmed;

  public ResumeSessionRequest() {
  }

  public ResumeSessionRequest(final String answer) {
    this(answer, null);
  }

  public ResumeSessionRequest(final String answer, final Boolean confirmed) {
    setMessage(answer);
    this.confirmed = confirmed;
  }

  public Boolean getConfirmed() {
    return confirmed;
  }

  public void setConfirmed(final Boolean confirmed) {
    this.confirmed = confirmed;
  }
}
