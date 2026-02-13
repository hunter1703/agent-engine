package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "mongodb")
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
