package com.agentengine.engine.api.exception;

public class ToolExecutionException extends AgentException {
  private final String toolName;

  public ToolExecutionException(String toolName, String message) {
    super(message);
    this.toolName = toolName;
  }

  public ToolExecutionException(String toolName, String message, Throwable cause) {
    super(message, cause);
    this.toolName = toolName;
  }

  public String getToolName() {
    return toolName;
  }
}
