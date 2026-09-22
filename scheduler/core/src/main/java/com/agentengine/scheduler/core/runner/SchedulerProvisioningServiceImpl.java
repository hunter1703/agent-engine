package com.agentengine.scheduler.core.runner;

import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.scheduler.core.store.SchedulerMongoStoreClientType;
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
public class SchedulerProvisioningServiceImpl implements SchedulerProvisioningService {

  private final MongoClientProvisioner mongoClientProvisioner;
  private final MicroServiceProvisioner microServiceProvisioner;

  @Inject
  public SchedulerProvisioningServiceImpl(
      final MongoClientProvisioner mongoClientProvisioner,
      final MicroServiceProvisioner microServiceProvisioner) {
    this.mongoClientProvisioner = mongoClientProvisioner;
    this.microServiceProvisioner = microServiceProvisioner;
  }

  @Override
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                SchedulerMongoStoreClientType.SCHEDULER,
                null,
                request.getServer(
                    ServerType.MONGO_SERVER, SchedulerMongoStoreClientType.SCHEDULER.name())));
    return run.result();
  }

  @Override
  public ProvisioningResult provision(final ProvisioningRequest request) {
    final int customerId = Context.requireCustomerId();
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                customerId,
                "scheduler",
                request.getServer(ServerType.MICROSERVICE_SERVER, "scheduler")));
    return run.result();
  }
}
