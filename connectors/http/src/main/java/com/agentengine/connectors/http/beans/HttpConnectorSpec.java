package com.agentengine.connectors.http.beans;

import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;

@JsonTypeName("HTTP")
public class HttpConnectorSpec extends ConnectorSpec {
  private String baseUrl;
  private String path;
  private Map<String, Object> queryParams;
  private Map<String, String> headers;
  private String method;
  private Map<String, Object> body;
  private AuthDecoratorSpec auth;

  public HttpConnectorSpec() {
    super(Type.HTTP);
  }

  public Map<String, Object> getBody() {
    return body;
  }

  public void setBody(Map<String, Object> body) {
    this.body = body;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  public Map<String, Object> getQueryParams() {
    return queryParams;
  }

  public void setQueryParams(Map<String, Object> queryParams) {
    this.queryParams = queryParams;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public AuthDecoratorSpec getAuth() {
    return auth;
  }

  public void setAuth(AuthDecoratorSpec auth) {
    this.auth = auth;
  }

  public String getUrl() {
    return buildUrl(getBaseUrl(), getPath());
  }

  private static String buildUrl(String baseUrl, String path) {
    if (!baseUrl.endsWith("/")) {
      baseUrl = baseUrl + "/";
    }
    if (path.startsWith("/")) {
      path = path.substring(1);
    }

    return baseUrl + path;
  }
}
