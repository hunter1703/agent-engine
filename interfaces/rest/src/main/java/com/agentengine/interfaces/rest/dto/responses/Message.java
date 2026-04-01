package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A message in a chat conversation. */
public record Message(
        String role,
        Object content,
        String name,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId,
        String refusal,
        String reasoning) {
    public Message(String role, String content) {
        this(role, content, null, null, null, null, null);
    }
}
