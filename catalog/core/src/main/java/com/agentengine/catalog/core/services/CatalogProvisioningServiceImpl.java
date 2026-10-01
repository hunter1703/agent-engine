package com.agentengine.catalog.core.services;

import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.catalog.core.repository.CatalogDocumentStoreClientType;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningRequest;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
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
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                Context.SYSTEM_CUSTOMER_ID,
                "catalog",
                request.getServer(ServerType.MICROSERVICE_SERVER, "catalog")));
    return run.result();
  }

  @Override
  public ProvisioningResult provision(final ProvisioningRequest request) {
    final String customerId = Context.requireCustomerId();
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                CatalogDocumentStoreClientType.CATALOG,
                customerId,
                request.getServer(
                    ServerType.MONGO_SERVER, CatalogDocumentStoreClientType.CATALOG.name())));
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
