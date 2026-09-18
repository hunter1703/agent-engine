package com.agentengine.util.sql;

import com.agentengine.util.infra.InfraClientConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.sql.SQLClientInfraConfig")
public class SQLClientInfraConfig extends InfraClientConfig {
  public static final String CATEGORY = "SQL";
  public static final String PEKKO_TYPE = "PEKKO";

  private String database;

  public String getDatabase() {
    return database;
  }

  public void setDatabase(final String database) {
    this.database = database;
  }
}
