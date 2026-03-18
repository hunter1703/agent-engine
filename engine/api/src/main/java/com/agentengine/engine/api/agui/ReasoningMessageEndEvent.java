package com.agentengine.engine.api.agui;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReasoningMessageEndEvent extends BaseReasoningEvent {

  @JsonProperty("type")
  public String getTypeName() {
    return "REASONING_MESSAGE_END";
  }
}
