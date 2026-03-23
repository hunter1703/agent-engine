package com.agentengine.interfaces.rest.dto;

public class ResumeSessionRequest extends AgentRequest {
  private Boolean confirmed;
  private String confirmationId;

  public ResumeSessionRequest() {
  }

  public ResumeSessionRequest(final String answer) {
    this(answer, null, null);
  }

  public ResumeSessionRequest(final String answer, final Boolean confirmed, final String confirmationId) {
    setMessage(answer);
    this.confirmed = confirmed;
    this.confirmationId = confirmationId;
  }

  public Boolean getConfirmed() {
    return confirmed;
  }

  public void setConfirmed(final Boolean confirmed) {
    this.confirmed = confirmed;
  }

  public String getConfirmationId() {
    return confirmationId;
  }

  public void setConfirmationId(final String confirmationId) {
    this.confirmationId = confirmationId;
  }
}
