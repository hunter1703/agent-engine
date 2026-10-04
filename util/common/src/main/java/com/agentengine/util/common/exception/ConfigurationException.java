package com.agentengine.util.common.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ConfigurationException extends RuntimeException implements ApplicationException {
  @JsonCreator
  public ConfigurationException(@JsonProperty("message") final String message) {
    super(message);
  }

  public ConfigurationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
