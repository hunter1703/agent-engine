package com.agentengine.catalog.core.services;

import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.catalog.core.repository.CatalogMongoStoreClientType;
import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.tenancy.ProvisioningRun;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
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
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    return new ProvisioningResult();
  }

  @Override
  public ProvisioningResult provision(final ProvisioningRequest request) {
    final int customerId = Context.requireCustomerId();
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                CatalogMongoStoreClientType.CATALOG,
                customerId,
                request.getServer(
                    ServerType.MONGO_SERVER, CatalogMongoStoreClientType.CATALOG.name())));
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                customerId,
                "catalog",
                request.getServer(ServerType.MICROSERVICE_SERVER, "catalog")));
    return run.result();
  }
}
