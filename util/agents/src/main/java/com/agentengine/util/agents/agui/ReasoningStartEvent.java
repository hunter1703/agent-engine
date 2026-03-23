package com.agentengine.util.agents.agui;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReasoningStartEvent extends BaseReasoningEvent {

  @JsonProperty("type")
  public String getTypeName() {
    return "REASONING_START";
  }
}
