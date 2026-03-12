package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthType;
import jakarta.inject.Singleton;

@Singleton
public final class NoAuthStrategy implements AuthStrategy {

  @Override
  public AuthType type() {
    return AuthType.NONE;
  }

  @Override
  public void apply(final AuthRequestContext requestContext) {
    // Intentionally empty.
  }
}
