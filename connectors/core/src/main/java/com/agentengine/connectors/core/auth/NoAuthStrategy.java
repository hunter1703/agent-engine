package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import java.util.Map;

final class NoAuthStrategy implements AuthStrategy {

  @Override
  public AuthType type() {
    return AuthType.NONE;
  }

  @Override
  public void apply(
      final AuthConfig authConfig,
      final RequestContext context,
      final TemplateResolver templateResolver,
      final boolean strictUnresolvedVariables,
      final Map<String, String> headers,
      final Map<String, String> queryParams) {
    // Intentionally empty.
  }
}
