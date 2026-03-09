package com.agentengine.connectors.core.config;

public record EndpointConfig(
    HttpMethod method,
    String baseUrl,
    String baseUrlTemplate,
    String path,
    String pathTemplate,
    Long connectTimeoutMs,
    Long readTimeoutMs,
    Long writeTimeoutMs,
    boolean omitNullHeaders,
    boolean omitNullQuery) {

  public EndpointConfig {
    method = method == null ? HttpMethod.UNKNOWN : method;
    omitNullHeaders = omitNullHeaders;
    omitNullQuery = omitNullQuery;
  }
}
