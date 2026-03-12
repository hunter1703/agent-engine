package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Singleton
public final class BasicAuthStrategy implements AuthStrategy {

  @Override
  public AuthType type() {
    return AuthType.BASIC;
  }

  @Override
  public void apply(final AuthRequestContext requestContext) {
    final AuthConfig authConfig = requestContext.authConfig();
    final String username =
        AuthTemplateUtils.resolveString(
            authConfig.username(),
            authConfig.usernameTemplate(),
            requestContext.requestContext(),
            requestContext.templateResolver(),
            requestContext.strictUnresolvedVariables(),
            "username");
    final String password =
        AuthTemplateUtils.resolveString(
            authConfig.password(),
            authConfig.passwordTemplate(),
            requestContext.requestContext(),
            requestContext.templateResolver(),
            requestContext.strictUnresolvedVariables(),
            "password");

    if (username == null || password == null) {
      throw new AuthStrategyException("Username and password are required for BASIC auth");
    }

    final String encoded =
        Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    requestContext.headers().put("Authorization", "Basic " + encoded);
  }
}
