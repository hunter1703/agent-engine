package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class BasicAuthStrategy implements AuthStrategy {

  @Override
  public AuthType type() {
    return AuthType.BASIC;
  }

  @Override
  public void apply(
      final AuthConfig authConfig,
      final RequestContext context,
      final TemplateResolver templateResolver,
      final boolean strictUnresolvedVariables,
      final Map<String, String> headers,
      final Map<String, String> queryParams) {
    final String username =
        AuthTemplateUtils.resolveString(
            authConfig.username(),
            authConfig.usernameTemplate(),
            context,
            templateResolver,
            strictUnresolvedVariables,
            "username");
    final String password =
        AuthTemplateUtils.resolveString(
            authConfig.password(),
            authConfig.passwordTemplate(),
            context,
            templateResolver,
            strictUnresolvedVariables,
            "password");

    if (username == null || password == null) {
      throw new AuthStrategyException("Username and password are required for BASIC auth");
    }

    final String encoded =
        Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    headers.put("Authorization", "Basic " + encoded);
  }
}
