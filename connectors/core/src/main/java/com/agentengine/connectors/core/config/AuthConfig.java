package com.agentengine.connectors.core.config;

import java.util.Map;

public record AuthConfig(
    AuthType type,
    String token,
    String tokenTemplate,
    String username,
    String usernameTemplate,
    String password,
    String passwordTemplate,
    String headerName,
    String headerNameTemplate,
    String queryParamName,
    String queryParamNameTemplate,
    String apiKey,
    String apiKeyTemplate,
    Map<String, Object> attributes) {

  public AuthConfig {
    type = type == null ? AuthType.UNKNOWN : type;
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }
}
