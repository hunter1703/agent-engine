package com.agentengine.util.agents.agui;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReasoningEndEvent extends BaseReasoningEvent {

    @JsonProperty("type")
    public String getTypeName() {
        return "REASONING_END";
    }
}
