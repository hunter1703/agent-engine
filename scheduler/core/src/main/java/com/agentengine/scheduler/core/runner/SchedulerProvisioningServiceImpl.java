package com.agentengine.scheduler.core.runner;

import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.scheduler.core.store.SchedulerMongoStoreClientType;
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
    final ProvisioningResult result = new ProvisioningResult();
    result.step(
        "mongo",
        () -> mongoClientProvisioner.provision(SchedulerMongoStoreClientType.SCHEDULER, null, request.getServer(MongoServerInfraConfig.TYPE, SchedulerMongoStoreClientType.SCHEDULER.name())));
    return result;
  }

  @Override
  public ProvisioningResult provision(final int customerId, final ProvisioningRequest request) {
    final ProvisioningResult result = new ProvisioningResult();
    result.step("microservice", () -> microServiceProvisioner.provision(customerId, "scheduler", request.getServer(MicroServiceServerInfraConfig.TYPE, "scheduler")));
    return result;
  }
}
