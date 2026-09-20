package com.agentengine.catalog.core.services;

import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.catalog.core.repository.CatalogMongoStoreClientType;
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
public class CatalogProvisioningServiceImpl implements CatalogProvisioningService {

  private final MongoClientProvisioner mongoClientProvisioner;
  private final MicroServiceProvisioner microServiceProvisioner;

  @Inject
  public CatalogProvisioningServiceImpl(
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
                CatalogMongoStoreClientType.CATALOG, customerId, request.getServer(MongoServerInfraConfig.TYPE, CatalogMongoStoreClientType.CATALOG.name())));
    result.step("microservice", () -> microServiceProvisioner.provision(customerId, "catalog", request.getServer(MicroServiceServerInfraConfig.TYPE, "catalog")));
    return result;
  }
}
