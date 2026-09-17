package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.infra.beans.AuthDecoratorSpec;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Objects;

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

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof HeaderAuthDecoratorSpec that)) {
      return false;
    }
    return Objects.equals(getType(), that.getType()) && Objects.equals(headers, that.headers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getType(), headers);
  }
}
