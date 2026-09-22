package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.services.ConnectorsProvisioningService;
import com.agentengine.connectors.core.ConnectorsMongoStoreClientType;
import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.tenancy.ProvisioningRun;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
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
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                UserContext.SYSTEM.customerId(),
                "connectors",
                request.getServer(ServerType.MICROSERVICE_SERVER, "connectors")));
    return run.result();
  }

  @Override
  public ProvisioningResult provision(final ProvisioningRequest request) {
    final int customerId = Context.requireCustomerId();
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                ConnectorsMongoStoreClientType.CONNECTORS,
                customerId,
                request.getServer(
                    ServerType.MONGO_SERVER, ConnectorsMongoStoreClientType.CONNECTORS.name())));
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                customerId,
                "connectors",
                request.getServer(ServerType.MICROSERVICE_SERVER, "connectors")));
    return run.result();
  }
}
