package com.agentengine.agent.core.session.events;

public final class PausedFact extends SessionFact {

  /** Non-null for child pauses; null for self pauses. */
  private String sessionId;

  /**
   * For self-pauses: the {@code adk_request_confirmation} wrapper call ID, which is also the {@code
   * interruptId} the client echoes back. For child pauses: the interrupt ID propagated from the
   * child.
   */
  private String correlationId;

  private String interruptId;
  private boolean internal;

  public PausedFact() {}

  private PausedFact(
      final String sessionId,
      final String correlationId,
      final String interruptId,
      final boolean internal) {
    this.sessionId = sessionId;
    this.correlationId = correlationId;
    this.interruptId = interruptId;
    this.internal = internal;
  }

  public static PausedFact childPaused(final String sessionId, final String interruptId) {
    return new PausedFact(sessionId, null, interruptId, false);
  }

  public static PausedFact externalSelfPaused(final String interruptId) {
    return new PausedFact(null, null, interruptId, false);
  }

  public static PausedFact internalSelfPause(final String correlationId, final String interruptId) {
    return new PausedFact(null, correlationId, interruptId, true);
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
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

  public String getCorrelationId() {
    return correlationId;
  }

  public void setCorrelationId(final String correlationId) {
    this.correlationId = correlationId;
  }
}
