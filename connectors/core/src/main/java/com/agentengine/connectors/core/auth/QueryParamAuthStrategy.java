package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import java.util.Map;

public final class QueryParamAuthStrategy implements AuthStrategy {

  private static final String DEFAULT_QUERY_PARAM = "api_key";

  @Override
  public AuthType type() {
    return AuthType.QUERY_PARAM;
  }

  @Override
  public void apply(
      final AuthConfig authConfig,
      final RequestContext context,
      final TemplateResolver templateResolver,
      final boolean strictUnresolvedVariables,
      final Map<String, String> headers,
      final Map<String, String> queryParams) {
    final String paramName =
        AuthTemplateUtils.resolveString(
            authConfig.queryParamName(),
            authConfig.queryParamNameTemplate(),
            context,
            templateResolver,
            strictUnresolvedVariables,
            "queryParamName");
    final String value =
        AuthTemplateUtils.resolveString(
            authConfig.apiKey(),
            authConfig.apiKeyTemplate(),
            context,
            templateResolver,
            strictUnresolvedVariables,
            "apiKey");

    if (value == null || value.isBlank()) {
      throw new AuthStrategyException("Auth value is required for QUERY_PARAM auth");
    }

    queryParams.put(
        paramName == null || paramName.isBlank() ? DEFAULT_QUERY_PARAM : paramName, value);
  }
}
