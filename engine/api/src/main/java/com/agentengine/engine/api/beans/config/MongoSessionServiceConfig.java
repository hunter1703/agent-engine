package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("mongodb")
@BsonDiscriminator(value = "mongodb")
public class MongoSessionServiceConfig extends SessionServiceConfig {

  private String connectionString;

  public MongoSessionServiceConfig() {
    super(SessionServiceType.MONGODB);
  }

  public String getConnectionString() {
    return connectionString;
  }

  public void setConnectionString(final String connectionString) {
    this.connectionString = connectionString;
  }
}
