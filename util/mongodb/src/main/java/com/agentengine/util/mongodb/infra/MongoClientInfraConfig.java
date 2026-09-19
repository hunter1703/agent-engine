package com.agentengine.util.mongodb.infra;

import com.agentengine.util.infra.InfraConfig;
import com.agentengine.util.mongodb.mongo.MongoUtils;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/**
 * The server a store's database is on: one customer's, or the one all customers share when the
 * config has no customer.
 */
@BsonDiscriminator(value = "com.agentengine.util.mongodb.infra.MongoClientInfraConfig")
public class MongoClientInfraConfig extends InfraConfig {
  public static final String TYPE = "MONGO_CLIENT";

  private String store;

  public MongoClientInfraConfig() {
    setType(TYPE);
  }

  @Override
  public String getId() {
    return MongoUtils.clientId(store, getCustomerId());
  }

  public String getStore() {
    return store;
  }

  public void setStore(final String store) {
    this.store = store;
  }
}
