package com.agentengine.internal;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.connectors.api.services.ConnectorsProvisioningService;
import com.agentengine.knowledge.api.services.KnowledgeProvisioningService;
import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.tenancy.ProvisioningService;
import com.agentengine.tenancy.TenancyMongoStoreClientType;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.crypto.EncryptionClientProvisioner;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class EnvironmentProvisioningService {

  private final MicroServiceClientProvider microServiceClientProvider;
  private final MongoClientProvisioner mongoClientProvisioner;
  private final EncryptionClientProvisioner encryptionClientProvisioner;

  @Inject
  public EnvironmentProvisioningService(
      final MicroServiceClientProvider microServiceClientProvider,
      final MongoClientProvisioner mongoClientProvisioner,
      final EncryptionClientProvisioner encryptionClientProvisioner) {
    this.microServiceClientProvider = microServiceClientProvider;
    this.mongoClientProvisioner = mongoClientProvisioner;
    this.encryptionClientProvisioner = encryptionClientProvisioner;
  }

  public ProvisioningResult provisionEnvironment(final ProvisioningRequest provisioningRequest) {
    final ProvisioningResult result = new ProvisioningResult();
    new Context(UUID.randomUUID().toString(), UserContext.SYSTEM)
        .run(
            () -> {
              result.step(
                  "encryption",
                  () -> encryptionClientProvisioner.provision(UserContext.SYSTEM.customerId(), null));
              result.step(
                  "tenancy",
                  () ->
                      mongoClientProvisioner.provision(
                          TenancyMongoStoreClientType.TENANCY, null, null));
              result.steps(
                  "catalog",
                  () -> client(CatalogProvisioningService.class).provisionEnvironment(provisioningRequest));
              result.steps(
                  "connectors",
                  () -> client(ConnectorsProvisioningService.class).provisionEnvironment(provisioningRequest));
              result.steps(
                  "knowledge",
                  () -> client(KnowledgeProvisioningService.class).provisionEnvironment(provisioningRequest));
              result.steps(
                  "agent", () -> client(AgentProvisioningService.class).provisionEnvironment(provisioningRequest));
              result.steps(
                  "scheduler",
                  () -> client(SchedulerProvisioningService.class).provisionEnvironment(provisioningRequest));
            });
    return result;
  }

  private <T extends ProvisioningService> T client(final Class<T> service) {
    return microServiceClientProvider.get(service);
  }
}
