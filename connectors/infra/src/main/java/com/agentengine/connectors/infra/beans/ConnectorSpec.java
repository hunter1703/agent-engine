package com.agentengine.connectors.infra.beans;

import java.util.Map;

public final class ConnectorSpec {
  private Map<String, AuthDecoratorSpec> auth;

  private ExecutorSpec executor;

  public Map<String, AuthDecoratorSpec> getAuth() {
    return auth;
  }

  public void setAuth(Map<String, AuthDecoratorSpec> auth) {
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
  public ConnectorSpec mergeWith(final ConnectorSpec appSpec, final boolean skipAuth) {
    if (appSpec == null) {
      return this;
    }
    if (!skipAuth && auth == null) {
      auth = appSpec.getAuth();
    }
    if (executor == null) {
      executor = appSpec.getExecutor();
    }
    return this;
  }
}
