package com.agentengine.engine.api.agui;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReasoningMessageContentEvent extends BaseReasoningEvent {
  private String delta;

  @JsonProperty("type")
  public String getTypeName() {
    return "REASONING_MESSAGE_CONTENT";
  }

  public String getDelta() {
    return delta;
  }

  public void setDelta(final String delta) {
    this.delta = delta;
  }
}
