package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import java.util.Map;

public record AuthRequestContext(
    AuthConfig authConfig,
    RequestContext requestContext,
    TemplateResolver templateResolver,
    boolean strictUnresolvedVariables,
    Map<String, String> headers,
    Map<String, String> queryParams) {}
