package com.agentengine.runtime.session.events;

public final class PausedFact extends SessionFact {

  private String sessionId;
  private String confirmationId;

  public PausedFact() {
  }

  public PausedFact(final String sessionId, final String confirmationId) {
    this.sessionId = sessionId;
    this.confirmationId = confirmationId;
  }

  public static PausedFact childPaused(final String sessionId, final String confirmationId) {
    return new PausedFact(sessionId, confirmationId);
  }

  public static PausedFact selfPaused(final String confirmationId) {
    return new PausedFact(null, confirmationId);
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }

  public String getConfirmationId() {
    return confirmationId;
  }

  public void setConfirmationId(final String confirmationId) {
    this.confirmationId = confirmationId;
  }
}
