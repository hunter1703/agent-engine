package com.agentengine.util.ms.client;

import com.agentengine.util.mongodb.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/**
 * MongoDB document that stores the gRPC endpoint for one microservice server.
 *
 * <p>One document per server ID (e.g. {@code "agent"}), stored in the {@code MicroServiceConfig}
 * collection of the {@code INFRA} database.
 */
@BsonDiscriminator(value = "com.agentengine.util.ms.client.MicroServiceInfraConfig")
public class MicroServiceInfraConfig extends InfraConfig {
  public static final String TYPE = "MICROSERVICE_SERVER";
  public static final String CATEGORY = "MICROSERVICE";

  /** Max inbound message size for gRPC: 100 MB. Used by both client and server. */
  public static final int MAX_INBOUND_MESSAGE_SIZE = 100 * 1024 * 1024;

  private String serverId;
  private String host;
  private int port;

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
