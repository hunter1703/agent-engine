package com.agentengine.connectors.http.beans;

import com.agentengine.connectors.infra.beans.Request;
import com.agentengine.util.common.CollectionUtils;
import java.util.Map;
import java.util.Objects;
import okhttp3.HttpUrl;

public class HttpRequest implements Request {
  private final String url;
  private final Map<String, Object> queryParams;
  private final String method;
  private final Map<String, Object> body;
  private final Map<String, String> headers;

  public HttpRequest(
      String url,
      Map<String, Object> queryParams,
      String method,
      Map<String, Object> body,
      Map<String, String> headers) {
    this.url = url;
    this.queryParams = queryParams;
    this.method = method;
    this.body = body;
    this.headers = CollectionUtils.nullSafeMutableMap(headers);
  }

  public String getFullUrl() {
    final HttpUrl parsed = HttpUrl.parse(url);

    final HttpUrl.Builder builder = Objects.requireNonNull(parsed).newBuilder();
    CollectionUtils.nullSafeMap(queryParams)
        .forEach((key, value) -> builder.addQueryParameter(key, String.valueOf(value)));
    return builder.build().toString();
  }

  public Map<String, Object> getQueryParams() {
    return queryParams;
  }

  public String getMethod() {
    return method;
  }

  public Map<String, Object> getBody() {
    return CollectionUtils.nullSafeMap(body);
  }

  public Map<String, String> getHeaders() {
    return CollectionUtils.nullSafeMap(headers);
  }

  public void addHeaders(final Map<String, String> additionalHeaders) {
    if (CollectionUtils.isEmpty(additionalHeaders)) {
      return;
    }
    headers.putAll(additionalHeaders);
  }

  @Override
  public Map<String, Object> getParameters() {
    return Map.of(
        "url", url,
        "queryParams", queryParams,
        "method", method,
        "body", body,
        "headers", headers);
  }
}
