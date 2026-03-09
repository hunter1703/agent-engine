package com.agentengine.connectors.core.runtime;

import java.util.Map;

public record ConnectorExecutionResult(
    int statusCode,
    boolean success,
    String requestUrl,
    String requestMethod,
    Map<String, String> responseHeaders,
    Object mappedData,
    Object metadata,
    String rawResponseBody,
    String errorCode,
    String errorMessage,
    boolean retryable) {

  public ConnectorExecutionResult {
    responseHeaders = responseHeaders == null ? Map.of() : Map.copyOf(responseHeaders);
  }
}
