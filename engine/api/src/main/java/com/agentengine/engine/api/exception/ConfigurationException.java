package com.agentengine.engine.api.exception;

public class ConfigurationException extends AgentException {
  public ConfigurationException(String message) {
    super(message);
  }

  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
