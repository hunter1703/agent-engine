package com.agentengine.util.ms.client;

import com.agentengine.util.infra.ClientType;
import com.agentengine.util.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.ms.client.MicroServiceClientInfraConfig")
public class MicroServiceClientInfraConfig extends InfraConfig {
  private String service;

  public MicroServiceClientInfraConfig() {
    setType(ClientType.MICROSERVICE_CLIENT.name());
  }

  @Override
  public String getId() {
    return MicroServiceUtils.clientId(getCustomerId(), getService());
  }

  public String getService() {
    return service;
  }

  public void setService(final String service) {
    this.service = service;
  }
}
