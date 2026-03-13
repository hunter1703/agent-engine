package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthType;

public interface AuthStrategy {

  AuthType type();

  void apply(AuthRequestContext requestContext);
}
