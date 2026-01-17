package com.agentengine.api;

public class InvokeResponse {
  private String sessionId;
  private String finalAnswer;
  private String thoughts;

  public InvokeResponse() {}

  public InvokeResponse(final String sessionId, final String finalAnswer, final String thoughts) {
    this.sessionId = sessionId;
    this.finalAnswer = finalAnswer;
    this.thoughts = thoughts;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }

  public String getFinalAnswer() {
    return finalAnswer;
  }

  public void setFinalAnswer(final String finalAnswer) {
    this.finalAnswer = finalAnswer;
  }

  public String getThoughts() {
    return thoughts;
  }

  public void setThoughts(final String thoughts) {
    this.thoughts = thoughts;
  }
}
