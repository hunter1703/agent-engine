package com.agentengine.util.infra;

public abstract class InfraClientConfig extends InfraConfig {
  public static final String DEFAULT_CLIENT_ID = "default";

  private String serverId;

  public String getServerId() {
    return serverId;
  }

  public void setServerId(final String serverId) {
    this.serverId = serverId;
  }
}
