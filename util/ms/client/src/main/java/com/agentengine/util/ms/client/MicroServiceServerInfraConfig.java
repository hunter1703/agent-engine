package com.agentengine.util.ms.client;

import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.ms.client.MicroServiceServerInfraConfig")
public class MicroServiceServerInfraConfig extends InfraConfig {
  public static final String TYPE = "MICROSERVICE_SERVER";

  public static final int MAX_INBOUND_MESSAGE_SIZE = 100 * 1024 * 1024;

  private String host;
  private int port;

  public MicroServiceServerInfraConfig() {
    setType(TYPE);
  }

  @Override
  public String getId() {
    return TYPE + ":" + getServerId();
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
