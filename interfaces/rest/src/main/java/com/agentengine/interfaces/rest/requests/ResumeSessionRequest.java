package com.agentengine.interfaces.rest.requests;

import jakarta.validation.constraints.NotBlank;

public class ResumeSessionRequest {
  @NotBlank(message = "message is required")
  private String message;

  public ResumeSessionRequest() {}

  public ResumeSessionRequest(final String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }
}
