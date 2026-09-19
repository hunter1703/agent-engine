package com.agentengine.util.vectordb;

import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.vectordb.VectorClientInfraConfig")
public class VectorClientInfraConfig extends InfraConfig {
  public static final String TYPE = "VECTOR_CLIENT";

  private String store;

  public VectorClientInfraConfig() {
    setType(TYPE);
  }

  @Override
  public String getId() {
    return VectorDbUtils.clientId(store, getCustomerId());
  }

  public String getStore() {
    return store;
  }

  public void setStore(final String store) {
    this.store = store;
  }
}
