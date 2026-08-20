package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("HEADER")
public class HeaderAuthDecoratorSpec extends AuthDecoratorSpec {

  /**
   * Either a map (each entry independently templatized) or a string containing a single Groovy
   * expression evaluating to the whole value at once - e.g. for building it programmatically.
   */
  private Object headers;

  public HeaderAuthDecoratorSpec() {
    super(Type.HEADER);
  }

  public Object getHeaders() {
    return headers;
  }

  public void setHeaders(Object headers) {
    this.headers = headers;
  }
}
