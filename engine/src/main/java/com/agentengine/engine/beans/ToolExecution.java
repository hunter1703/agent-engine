package com.agentengine.engine.beans;

import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.Role;
import com.agentengine.engine.message.ToolCall;
import java.util.List;
import java.time.Instant;

public final class ToolExecution {
  private String id;
  private final ToolCall toolCall;
  private final String status;
  private final String output;
  private final Instant startedAt;
  private final long durationMs;

  public ToolExecution(final ToolCall toolCall, final String status, final String output, final Instant startedAt,
      final long durationMs) {
    this.toolCall = toolCall;
    this.status = status;
    this.output = output;
    this.startedAt = startedAt;
    this.durationMs = durationMs;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public ToolCall getToolCall() {
    return toolCall;
  }

  public String getStatus() {
    return status;
  }

  public String getOutput() {
    return output;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public long getDurationMs() {
    return durationMs;
  }
}
