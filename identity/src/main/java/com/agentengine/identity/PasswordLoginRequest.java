package com.agentengine.identity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PasswordLoginRequest extends LoginRequest {

  private final String username;
  private final String password;

  @JsonCreator
  public PasswordLoginRequest(
      @JsonProperty("username") final String username,
      @JsonProperty("password") final String password) {
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
