package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.services.ConnectorsProvisioningService;
import com.agentengine.connectors.core.ConnectorsMongoStoreClientType;
import com.agentengine.util.mongodb.infra.MongoServerInfraConfig;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.util.ms.client.MicroServiceServerInfraConfig;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class ConnectorsProvisioningServiceImpl implements ConnectorsProvisioningService {

  private final MongoClientProvisioner mongoClientProvisioner;
  private final MicroServiceProvisioner microServiceProvisioner;

  @Inject
  public ConnectorsProvisioningServiceImpl(
      final MongoClientProvisioner mongoClientProvisioner,
      final MicroServiceProvisioner microServiceProvisioner) {
    this.mongoClientProvisioner = mongoClientProvisioner;
    this.microServiceProvisioner = microServiceProvisioner;
  }

  @Override
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest provisioningRequest) {
      return new ProvisioningResult();
  }

  @Override
  public ProvisioningResult provision(final int customerId, final ProvisioningRequest request) {
    final ProvisioningResult result = new ProvisioningResult();
    result.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                ConnectorsMongoStoreClientType.CONNECTORS, customerId, request.getServer(MongoServerInfraConfig.TYPE, ConnectorsMongoStoreClientType.CONNECTORS.name())));
    result.step("microservice", () -> microServiceProvisioner.provision(customerId, "connectors", request.getServer(MicroServiceServerInfraConfig.TYPE, "connectors")));
    return result;
  }
}
