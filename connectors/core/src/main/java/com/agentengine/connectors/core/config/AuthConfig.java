package com.agentengine.connectors.core.config;

import java.util.Map;

public record AuthConfig(
    String type,
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
    type = type == null || type.isBlank() ? AuthType.UNKNOWN.name() : type;
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }

  public AuthConfig(
      final AuthType type,
      final String token,
      final String tokenTemplate,
      final String username,
      final String usernameTemplate,
      final String password,
      final String passwordTemplate,
      final String headerName,
      final String headerNameTemplate,
      final String queryParamName,
      final String queryParamNameTemplate,
      final String apiKey,
      final String apiKeyTemplate,
      final Map<String, Object> attributes) {
    this(
        type == null ? null : type.name(),
        token,
        tokenTemplate,
        username,
        usernameTemplate,
        password,
        passwordTemplate,
        headerName,
        headerNameTemplate,
        queryParamName,
        queryParamNameTemplate,
        apiKey,
        apiKeyTemplate,
        attributes);
  }

  public AuthType typeEnum() {
    return AuthType.valueOfOrDefault(type);
  }
}
