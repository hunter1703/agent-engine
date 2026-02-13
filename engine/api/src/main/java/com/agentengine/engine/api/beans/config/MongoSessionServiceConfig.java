package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "mongodb")
@BsonDiscriminator(value = "mongodb")
public class MongoSessionServiceConfig extends SessionServiceConfig {

  public static final String DEFAULT_DATABASE = "AGENT_ENGINE";
  public static final String DEFAULT_COLLECTION = "Session";

  private String connectionString;
  private String database = DEFAULT_DATABASE;
  private String collection = DEFAULT_COLLECTION;

  public MongoSessionServiceConfig() {
    super(SessionServiceType.MONGODB);
  }

  public String getConnectionString() {
    return connectionString;
  }

  public void setConnectionString(final String connectionString) {
    this.connectionString = connectionString;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(final String database) {
    this.database = database;
  }

  public String getCollection() {
    return collection;
  }

  public void setCollection(final String collection) {
    this.collection = collection;
  }
}
