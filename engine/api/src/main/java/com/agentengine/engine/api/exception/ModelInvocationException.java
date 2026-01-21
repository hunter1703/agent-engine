package com.agentengine.engine.api.exception;

public class ModelInvocationException extends AgentException {
  private final String modelId;

  public ModelInvocationException(String modelId, String message) {
    super(message);
    this.modelId = modelId;
  }

  public ModelInvocationException(String modelId, String message, Throwable cause) {
    super(message, cause);
    this.modelId = modelId;
  }

  public String getModelId() {
    return modelId;
  }
}
