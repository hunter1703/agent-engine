package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import jakarta.inject.Singleton;

@Singleton
public final class BearerTokenAuthStrategy implements AuthStrategy {

  @Override
  public AuthType type() {
    return AuthType.BEARER_TOKEN;
  }

  @Override
  public void apply(final AuthRequestContext requestContext) {
    final AuthConfig authConfig = requestContext.authConfig();
    final String token = AuthTemplateUtils.resolveString(authConfig.token(), authConfig.tokenTemplate(), requestContext.requestContext(),
        requestContext.templateResolver(), requestContext.strictUnresolvedVariables(), "token");

    if (token == null || token.isBlank()) {
      throw new AuthStrategyException("Token value is required for BEARER_TOKEN auth");
    }

    requestContext.headers().put("Authorization", "Bearer " + token.trim());
  }
}
