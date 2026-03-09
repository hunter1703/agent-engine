package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import java.util.Map;

public final class BearerTokenAuthStrategy implements AuthStrategy {

  @Override
  public AuthType type() {
    return AuthType.BEARER_TOKEN;
  }

  @Override
  public void apply(
      final AuthConfig authConfig,
      final RequestContext context,
      final TemplateResolver templateResolver,
      final boolean strictUnresolvedVariables,
      final Map<String, String> headers,
      final Map<String, String> queryParams) {
    final String token =
        AuthTemplateUtils.resolveString(
            authConfig.token(),
            authConfig.tokenTemplate(),
            context,
            templateResolver,
            strictUnresolvedVariables,
            "token");

    if (token == null || token.isBlank()) {
      throw new AuthStrategyException("Token value is required for BEARER_TOKEN auth");
    }

    headers.put("Authorization", "Bearer " + token.trim());
  }
}
