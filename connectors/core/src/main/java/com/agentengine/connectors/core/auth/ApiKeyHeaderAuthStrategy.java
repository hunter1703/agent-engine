package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import java.util.Map;

public final class ApiKeyHeaderAuthStrategy implements AuthStrategy {

  private static final String DEFAULT_HEADER = "X-API-Key";

  @Override
  public AuthType type() {
    return AuthType.API_KEY_HEADER;
  }

  @Override
  public void apply(
      final AuthConfig authConfig,
      final RequestContext context,
      final TemplateResolver templateResolver,
      final boolean strictUnresolvedVariables,
      final Map<String, String> headers,
      final Map<String, String> queryParams) {
    final String headerName =
        AuthTemplateUtils.resolveString(
            authConfig.headerName(),
            authConfig.headerNameTemplate(),
            context,
            templateResolver,
            strictUnresolvedVariables,
            "headerName");
    final String apiKey =
        AuthTemplateUtils.resolveString(
            authConfig.apiKey(),
            authConfig.apiKeyTemplate(),
            context,
            templateResolver,
            strictUnresolvedVariables,
            "apiKey");

    if (apiKey == null || apiKey.isBlank()) {
      throw new AuthStrategyException("API key value is required for API_KEY_HEADER auth");
    }

    headers.put(
        headerName == null || headerName.isBlank() ? DEFAULT_HEADER : headerName,
        apiKey.trim());
  }
}
