package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraClientProvisioner;
import com.agentengine.util.mongodb.infra.MongoClientInfraConfig;
import com.agentengine.util.mongodb.infra.MongoServerInfraConfig;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MongoClientProvisioner extends InfraClientProvisioner {

  private final InfraConfigService infraConfigService;
  private final Instance<AbstractMongoReadRepository<?>> repositories;

  @Inject
  public MongoClientProvisioner(
          final InfraConfigService infraConfigService,
          ApplicationConfig applicationConfig,
          final Instance<AbstractMongoReadRepository<?>> repositories) {
    super(applicationConfig);
    this.infraConfigService = infraConfigService;
    this.repositories = repositories;
  }

  public void provision(
      final MongoStoreClientType store,
      final Integer customerId,
      String serverId) {
    final MongoClientInfraConfig clientConfig = MongoUtils.clientConfig(
            store.name(), customerId, resolvedServerId(MongoServerInfraConfig.TYPE, serverId));
    infraConfigService.save(clientConfig);
    for (final AbstractMongoReadRepository<?> repository : repositories) {
      if (repository.clientType().name().equals(store.name())) {
        repository.setup();
      }
    }
  }
}
