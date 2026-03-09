package com.agentengine.connectors.core.template;

final class UnresolvedVariableException extends RuntimeException {

  UnresolvedVariableException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
