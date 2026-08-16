package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Delta message in a streaming chunk. */
public record MessageDelta(
    String role,
    String content,
    String refusal,
    @JsonProperty("tool_calls") List<ToolCall> toolCalls,
    @JsonProperty("tool_call_id") String toolCallId,
    String reasoning) {
  public MessageDelta() {
    this(null, null, null, null, null, null);
  }
}
