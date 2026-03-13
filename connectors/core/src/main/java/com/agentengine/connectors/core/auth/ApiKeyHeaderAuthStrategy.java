package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import jakarta.inject.Singleton;

@Singleton
public final class ApiKeyHeaderAuthStrategy implements AuthStrategy {

  private static final String DEFAULT_HEADER = "X-API-Key";

  @Override
  public AuthType type() {
    return AuthType.API_KEY_HEADER;
  }

  @Override
  public void apply(final AuthRequestContext requestContext) {
    final AuthConfig authConfig = requestContext.authConfig();
    final String headerName = AuthTemplateUtils.resolveString(authConfig.headerName(), authConfig.headerNameTemplate(),
        requestContext.requestContext(), requestContext.templateResolver(), requestContext.strictUnresolvedVariables(), "headerName");
    final String apiKey = AuthTemplateUtils.resolveString(authConfig.apiKey(), authConfig.apiKeyTemplate(), requestContext.requestContext(),
        requestContext.templateResolver(), requestContext.strictUnresolvedVariables(), "apiKey");

    if (apiKey == null || apiKey.isBlank()) {
      throw new AuthStrategyException("API key value is required for API_KEY_HEADER auth");
    }

    requestContext.headers().put(headerName == null || headerName.isBlank() ? DEFAULT_HEADER : headerName, apiKey.trim());
  }
}
