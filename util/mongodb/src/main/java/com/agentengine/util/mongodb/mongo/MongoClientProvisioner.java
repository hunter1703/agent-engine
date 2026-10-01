package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentStoreClientType;
import com.agentengine.util.infra.InfraClientProvisioner;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.mongodb.infra.MongoClientInfraConfig;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MongoClientProvisioner extends InfraClientProvisioner {

  private final InfraConfigService infraConfigService;
  private final Instance<DocumentBackend> documentBackends;

  @Inject
  public MongoClientProvisioner(
      final InfraConfigService infraConfigService,
      final ApplicationConfig applicationConfig,
      @Any final Instance<DocumentBackend> documentBackends) {
    super(applicationConfig);
    this.infraConfigService = infraConfigService;
    this.documentBackends = documentBackends;
  }

  public void provision(
      final DocumentStoreClientType store, final String customerId, final String serverId) {
    final MongoClientInfraConfig clientConfig =
        MongoUtils.clientConfig(
            store.name(), customerId, resolvedServerId(ServerType.MONGO_SERVER, serverId));
    infraConfigService.save(clientConfig);
    documentBackends.forEach(documentBackend -> documentBackend.setup(store, customerId));
  }
}
