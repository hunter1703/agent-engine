package com.agentengine.util.sql;

import com.agentengine.util.infra.ClientType;
import com.agentengine.util.infra.InfraConfig;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.sql.SQLClientInfraConfig")
public class SQLClientInfraConfig extends InfraConfig {
  private String store;

  public SQLClientInfraConfig() {
    setType(ClientType.SQL_CLIENT.name());
  }

  @Override
  public String getId() {
    return SQLUtils.clientId(store, getCustomerId());
  }

  public String getStore() {
    return store;
  }

  public void setStore(final String store) {
    this.store = store;
  }

  public String schema() {
    return store.toLowerCase(Locale.ROOT) + "_" + getCustomerId();
  }
}
