package com.agentengine.engine.api.agui;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReasoningStartEvent extends BaseReasoningEvent {

  @JsonProperty("type")
  public String getTypeName() {
    return "REASONING_START";
  }
}
