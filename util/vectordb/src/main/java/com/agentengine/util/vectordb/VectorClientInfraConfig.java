package com.agentengine.util.vectordb;

import com.agentengine.util.infra.ClientType;
import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.vectordb.VectorClientInfraConfig")
public class VectorClientInfraConfig extends InfraConfig {
  private String store;

  public VectorClientInfraConfig() {
    setType(ClientType.VECTOR_CLIENT.name());
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
