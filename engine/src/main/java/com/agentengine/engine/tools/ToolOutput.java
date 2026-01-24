package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import java.time.Instant;

public record ToolOutput(ToolCall toolCall, String status, String output, Instant startedAt, long durationMs) {

  public static ToolOutput unknown(final ToolCall toolCall) {
    return new ToolOutput(toolCall, "unknown",
        toolCall == null ? "Unknown tool" : "Unknown tool: " + toolCall.name(), Instant.now(), 0);
  }

  public static ToolOutput error(final ToolCall toolCall, final String message) {
    return new ToolOutput(toolCall, "error", message, Instant.now(), 0);
  }

  public ToolExecution toExecution() {
    return new ToolExecution(toolCall, status, output, startedAt, durationMs);
  }
}
