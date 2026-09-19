package com.agentengine.util.sql;

import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/** The server a store's database is on, and the database's name there. */
@BsonDiscriminator(value = "com.agentengine.util.sql.SQLClientInfraConfig")
public class SQLClientInfraConfig extends InfraConfig {
  public static final String TYPE = "SQL_CLIENT";
  public static final String PEKKO = "PEKKO";

  private String store;
  private String database;

  public SQLClientInfraConfig() {
    setType(TYPE);
  }

  @Override
  public String getId() {
    return TYPE + ":" + store + ":" + getCustomerId();
  }

  public String getStore() {
    return store;
  }

  public void setStore(final String store) {
    this.store = store;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(final String database) {
    this.database = database;
  }
}
