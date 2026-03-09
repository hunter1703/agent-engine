package com.agentengine.connectors.core.auth;

public final class AuthStrategyException extends RuntimeException {

  public AuthStrategyException(final String message) {
    super(message);
  }

  public AuthStrategyException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
