package com.agentengine.agent.core.session.events;

public final class CompletedFact extends SessionFact {

  private String finalAnswer;
  private String error;

  public CompletedFact() {
    this(null, null);
  }

  public CompletedFact(final String finalAnswer, final String error) {
    this.finalAnswer = finalAnswer;
    this.error = error;
  }

  public String getFinalAnswer() {
    return finalAnswer;
  }

  public void setFinalAnswer(final String finalAnswer) {
    this.finalAnswer = finalAnswer;
  }

  public String getError() {
    return error;
  }

  public void setError(final String error) {
    this.error = error;
  }
}
