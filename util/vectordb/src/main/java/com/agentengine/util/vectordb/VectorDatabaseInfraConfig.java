package com.agentengine.util.vectordb;

import com.agentengine.util.mongodb.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/**
 * Infra config for the Qdrant vector database connection.
 *
 * <p>Stored in {@code INFRA.InfraConfig} under id {@code QDRANT:default} and seeded at deploy time
 * by {@code seed-infra-configs.sh}, which overrides {@link #host} to the in-cluster Qdrant service
 * name. Defaults target a local Qdrant instance.
 */
@BsonDiscriminator(value = "com.agentengine.util.vectordb.VectorDatabaseInfraConfig")
public class VectorDatabaseInfraConfig extends InfraConfig {

  public static final String TYPE = "VECTOR_SERVER";
  public static final String CATEGORY = "VECTOR";
  public static final String CONFIG_ID = "default";

  private String host = "localhost";
  private int httpPort = 6333;
  private String apiKey = null;

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

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(final String apiKey) {
    this.apiKey = apiKey;
  }
}
