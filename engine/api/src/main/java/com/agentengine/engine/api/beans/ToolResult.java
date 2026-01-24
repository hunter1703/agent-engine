package com.agentengine.engine.api.beans;

public record ToolResult(String output, String status) {

  public static ToolResult ok(String output) {
    return new ToolResult(output, "ok");
  }

  public static ToolResult error(String errorMessage) {
    return new ToolResult(errorMessage, "error");
  }

  public static ToolResult clarification(final String payload) {
    return new ToolResult(payload, "clarification_required");
  }
}
