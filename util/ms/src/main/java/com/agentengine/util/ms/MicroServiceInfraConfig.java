package com.agentengine.util.ms;

import com.agentengine.util.common.beans.BaseEntity;

/**
 * MongoDB document that stores the gRPC endpoint for one microservice server.
 *
 * <p>
 * One document per server ID (e.g. {@code "agent"}), stored in the
 * {@code MicroServiceConfig} collection of the {@code INFRA} database.
 */
public class MicroServiceInfraConfig extends BaseEntity {

  private String serverId;
  private String host;
  private int port;

  public MicroServiceInfraConfig() {
  }

  public String getServerId() {
    return serverId;
  }

  public void setServerId(final String serverId) {
    this.serverId = serverId;
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(final int port) {
    this.port = port;
  }
}
