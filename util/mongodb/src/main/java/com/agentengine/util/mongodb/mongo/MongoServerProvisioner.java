package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.mongodb.infra.MongoServerInfraConfig;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraServerProvisioner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MongoServerProvisioner extends InfraServerProvisioner<MongoServerInfraConfig> {

  @Inject
  public MongoServerProvisioner(final InfraConfigService infraConfigService) {
    super(infraConfigService);
  }
}
