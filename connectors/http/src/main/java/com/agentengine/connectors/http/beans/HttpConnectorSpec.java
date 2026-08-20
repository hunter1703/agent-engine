package com.agentengine.connectors.http.beans;

import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("HTTP")
public class HttpConnectorSpec extends ConnectorSpec {
  private String baseUrl;
  private String path;

  /**
   * queryParams, headers, and body are each either a map (each entry independently templatized) or
   * a string containing a single Groovy expression evaluating to the whole value at once - e.g. for
   * building it programmatically.
   */
  private Object queryParams;

  private Object headers;
  private String method;
  private Object body;
  private AuthDecoratorSpec auth;

  public HttpConnectorSpec() {
    super(Type.HTTP);
  }

  public Object getBody() {
    return body;
  }

  public void setBody(Object body) {
    this.body = body;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public Object getHeaders() {
    return headers;
  }

  public void setHeaders(Object headers) {
    this.headers = headers;
  }

  public Object getQueryParams() {
    return queryParams;
  }

  public void setQueryParams(Object queryParams) {
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
