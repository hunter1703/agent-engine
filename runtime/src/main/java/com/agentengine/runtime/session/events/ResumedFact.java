package com.agentengine.runtime.session.events;

public final class ResumedFact extends SessionFact {

  private String confirmationId;

  public ResumedFact() {
  }

  public ResumedFact(final String confirmationId) {
    this.confirmationId = confirmationId;
  }

  public String getConfirmationId() {
    return confirmationId;
  }

  public void setConfirmationId(final String confirmationId) {
    this.confirmationId = confirmationId;
  }
}
