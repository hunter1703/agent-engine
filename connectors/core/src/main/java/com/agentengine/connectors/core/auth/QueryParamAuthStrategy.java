package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import jakarta.inject.Singleton;

@Singleton
public final class QueryParamAuthStrategy implements AuthStrategy {

  private static final String DEFAULT_QUERY_PARAM = "api_key";

  @Override
  public AuthType type() {
    return AuthType.QUERY_PARAM;
  }

  @Override
  public void apply(final AuthRequestContext requestContext) {
    final AuthConfig authConfig = requestContext.authConfig();
    final String paramName =
        AuthTemplateUtils.resolveString(
            authConfig.queryParamName(),
            authConfig.queryParamNameTemplate(),
            requestContext.requestContext(),
            requestContext.templateResolver(),
            requestContext.strictUnresolvedVariables(),
            "queryParamName");
    final String value =
        AuthTemplateUtils.resolveString(
            authConfig.apiKey(),
            authConfig.apiKeyTemplate(),
            requestContext.requestContext(),
            requestContext.templateResolver(),
            requestContext.strictUnresolvedVariables(),
            "apiKey");

    if (value == null || value.isBlank()) {
      throw new AuthStrategyException("Auth value is required for QUERY_PARAM auth");
    }

    requestContext.queryParams().put(
        paramName == null || paramName.isBlank() ? DEFAULT_QUERY_PARAM : paramName, value);
  }
}
