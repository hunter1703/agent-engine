package com.agentengine.connectors.core.config;

public record EndpointConfig(String method, String baseUrl, String baseUrlTemplate, String path, String pathTemplate, Long connectTimeoutMs,
    Long readTimeoutMs, Long writeTimeoutMs, boolean omitNullHeaders, boolean omitNullQuery) {

  public EndpointConfig {
    method = method == null || method.isBlank() ? HttpMethod.UNKNOWN.name() : method;
    omitNullHeaders = omitNullHeaders;
    omitNullQuery = omitNullQuery;
  }

  public EndpointConfig(final HttpMethod method, final String baseUrl, final String baseUrlTemplate, final String path,
      final String pathTemplate, final Long connectTimeoutMs, final Long readTimeoutMs, final Long writeTimeoutMs,
      final boolean omitNullHeaders, final boolean omitNullQuery) {
    this(method == null ? null : method.name(), baseUrl, baseUrlTemplate, path, pathTemplate, connectTimeoutMs, readTimeoutMs,
        writeTimeoutMs, omitNullHeaders, omitNullQuery);
  }

  public HttpMethod methodEnum() {
    return HttpMethod.valueOfOrDefault(method);
  }
}
