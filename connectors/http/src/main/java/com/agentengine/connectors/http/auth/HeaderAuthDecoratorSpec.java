package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;

@JsonTypeName("HEADER")
public class HeaderAuthDecoratorSpec extends AuthDecoratorSpec {
  private Map<String, String> headers;

  public HeaderAuthDecoratorSpec() {
    super(Type.HEADER);
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }
}
