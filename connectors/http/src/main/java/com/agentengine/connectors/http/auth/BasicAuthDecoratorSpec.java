package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.infra.beans.AuthDecoratorSpec;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Objects;

@JsonTypeName("BASIC")
public class BasicAuthDecoratorSpec extends AuthDecoratorSpec {

  /** Templated expressions for the username and password to base64-encode into the header. */
  private String username;

  private String password;

  public BasicAuthDecoratorSpec() {
    super(Type.BASIC);
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BasicAuthDecoratorSpec that)) {
      return false;
    }
    return Objects.equals(getType(), that.getType())
        && Objects.equals(username, that.username)
        && Objects.equals(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getType(), username, password);
  }
}
