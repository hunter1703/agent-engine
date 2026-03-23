package com.agentengine.util.agents.agui;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReasoningMessageStartEvent extends BaseReasoningEvent {
  private String role;

  @JsonProperty("type")
  public String getTypeName() {
    return "REASONING_MESSAGE_START";
  }

  public String getRole() {
    return role;
  }

  public void setRole(final String role) {
    this.role = role;
  }
}
