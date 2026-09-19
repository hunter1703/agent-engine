package com.agentengine.util.vectordb;

import com.agentengine.util.common.Secure;
import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/**
 * Connection details of a Qdrant server, shared by the uses that connect to it — each a {@link
 * VectorClientInfraConfig}. Defaults target a local Qdrant instance.
 */
@BsonDiscriminator(value = "com.agentengine.util.vectordb.VectorServerInfraConfig")
public class VectorServerInfraConfig extends InfraConfig {

  public static final String TYPE = "VECTOR_SERVER";

  private String host = "localhost";
  private int httpPort = 6333;
  private int grpcPort = 6334;
  @Secure private String apiKey = null;
  private boolean tls = false;

  public VectorServerInfraConfig() {
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

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(final int httpPort) {
    this.httpPort = httpPort;
  }

  public int getGrpcPort() {
    return grpcPort;
  }

  public void setGrpcPort(final int grpcPort) {
    this.grpcPort = grpcPort;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(final String apiKey) {
    this.apiKey = apiKey;
  }

  public boolean isTls() {
    return tls;
  }

  public void setTls(final boolean tls) {
    this.tls = tls;
  }
}
