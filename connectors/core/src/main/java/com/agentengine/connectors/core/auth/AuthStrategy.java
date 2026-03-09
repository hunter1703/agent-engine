package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import java.util.Map;

public interface AuthStrategy {

  AuthType type();

  void apply(
      AuthConfig authConfig,
      RequestContext context,
      TemplateResolver templateResolver,
      boolean strictUnresolvedVariables,
      Map<String, String> headers,
      Map<String, String> queryParams);
}
