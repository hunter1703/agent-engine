package com.agentengine.connectors.infra.beans;

import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;

public final class ConnectorSpec {
  private AuthDecoratorSpec auth;
  private ExecutorSpec executor;

  public AuthDecoratorSpec getAuth() {
    return auth;
  }

  public void setAuth(AuthDecoratorSpec auth) {
    this.auth = auth;
  }

  public ExecutorSpec getExecutor() {
    return executor;
  }

  public void setExecutor(ExecutorSpec executor) {
    this.executor = executor;
  }

  /**
   * Overlays {@code appSpec} onto whichever of this instance's own fields are unset. Merging
   * happens only at this level - {@code auth} and {@code executor} are each taken wholesale from
   * one side or the other, with no merging of fields within an {@link ExecutorSpec} itself.
   */
  public ConnectorSpec mergeWith(final ConnectorSpec appSpec) {
    if (appSpec == null) {
      return this;
    }
    if (auth == null) {
      auth = appSpec.getAuth();
    }
    if (executor == null) {
      executor = appSpec.getExecutor();
    }
    return this;
  }
}
