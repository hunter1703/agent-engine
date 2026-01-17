package com.agentengine.api;

public class ToolExecutionDto {
  private String id;
  private String toolName;
  private String status;
  private String output;
  private long durationMs;

  public ToolExecutionDto() {}

  public ToolExecutionDto(
      final String id,
      final String toolName,
      final String status,
      final String output,
      final long durationMs) {
    this.id = id;
    this.toolName = toolName;
    this.status = status;
    this.output = output;
    this.durationMs = durationMs;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getToolName() {
    return toolName;
  }

  public void setToolName(final String toolName) {
    this.toolName = toolName;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(final String output) {
    this.output = output;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(final long durationMs) {
    this.durationMs = durationMs;
  }
}
