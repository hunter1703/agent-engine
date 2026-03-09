package com.agentengine.connectors.core.http;

public final class HttpTransportException extends RuntimeException {

  public HttpTransportException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public HttpTransportException(final String message) {
    super(message);
  }
}
