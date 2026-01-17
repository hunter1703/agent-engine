package com.agentengine.api;

public class InvokeRequest extends AgentRequest {
  private String message;

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }
}
