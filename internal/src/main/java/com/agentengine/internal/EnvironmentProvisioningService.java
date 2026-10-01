package com.agentengine.internal;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.connectors.api.services.ConnectorsProvisioningService;
import com.agentengine.identity.IdentityDocumentStoreClientType;
import com.agentengine.knowledge.api.services.KnowledgeProvisioningService;
import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.tenancy.TenancyProvisioningService;
import com.agentengine.util.context.Context;
import com.agentengine.util.crypto.EncryptionClientProvisioner;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningRequest;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
import com.agentengine.util.infra.provisioning.ProvisioningService;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class EnvironmentProvisioningService {

  private final MicroServiceClientProvider microServiceClientProvider;
  private final EncryptionClientProvisioner encryptionClientProvisioner;
  private final MongoClientProvisioner mongoClientProvisioner;

  @Inject
  public EnvironmentProvisioningService(
      final MicroServiceClientProvider microServiceClientProvider,
      final EncryptionClientProvisioner encryptionClientProvisioner,
      final MongoClientProvisioner mongoClientProvisioner) {
    this.microServiceClientProvider = microServiceClientProvider;
    this.encryptionClientProvisioner = encryptionClientProvisioner;
    this.mongoClientProvisioner = mongoClientProvisioner;
  }

  public ProvisioningResult provisionEnvironment(final ProvisioningRequest provisioningRequest) {
    final ProvisioningRun run = new ProvisioningRun();
    Context.asSystemCustomer()
        .run(
            () -> {
              run.step(
                  "encryption",
                  () -> encryptionClientProvisioner.provision(Context.SYSTEM_CUSTOMER_ID, null));
              run.step(
                  "identity",
                  () ->
                      mongoClientProvisioner.provision(
                          IdentityDocumentStoreClientType.IDENTITY,
                          null,
                          provisioningRequest.getServer(
                              ServerType.MONGO_SERVER,
                              IdentityDocumentStoreClientType.IDENTITY.name())));
              run.merge(
                  "tenancy",
                  () ->
                      client(TenancyProvisioningService.class)
                          .provisionEnvironment(provisioningRequest));
              run.merge(
                  "catalog",
                  () ->
                      client(CatalogProvisioningService.class)
                          .provisionEnvironment(provisioningRequest));
              run.merge(
                  "connectors",
                  () ->
                      client(ConnectorsProvisioningService.class)
                          .provisionEnvironment(provisioningRequest));
              run.merge(
                  "knowledge",
                  () ->
                      client(KnowledgeProvisioningService.class)
                          .provisionEnvironment(provisioningRequest));
              run.merge(
                  "agent",
                  () ->
                      client(AgentProvisioningService.class)
                          .provisionEnvironment(provisioningRequest));
              run.merge(
                  "scheduler",
                  () ->
                      client(SchedulerProvisioningService.class)
                          .provisionEnvironment(provisioningRequest));
            });
    return run.result();
  }

  private <T extends ProvisioningService> T client(final Class<T> service) {
    return microServiceClientProvider.get(service);
  }
}
