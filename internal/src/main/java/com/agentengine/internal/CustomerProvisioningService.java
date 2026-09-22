package com.agentengine.internal;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.connectors.api.services.ConnectorsProvisioningService;
import com.agentengine.knowledge.api.services.KnowledgeProvisioningService;
import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.tenancy.Customer;
import com.agentengine.tenancy.CustomerRepository;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.tenancy.ProvisioningRun;
import com.agentengine.tenancy.ProvisioningService;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.cloudstorage.CloudStorageClientProvisioner;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.crypto.EncryptionClientProvisioner;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.UUID;

@Singleton
public class CustomerProvisioningService {

  private final MicroServiceClientProvider microServiceClientProvider;
  private final CustomerRepository customerRepository;
  private final EncryptionClientProvisioner encryptionClientProvisioner;
  private final CloudStorageClientProvisioner cloudStorageClientProvisioner;
  private final DefaultModelsRepository defaultModelsRepository;

  @Inject
  public CustomerProvisioningService(
      final MicroServiceClientProvider microServiceClientProvider,
      final CustomerRepository customerRepository,
      final EncryptionClientProvisioner encryptionClientProvisioner,
      final CloudStorageClientProvisioner cloudStorageClientProvisioner,
      final DefaultModelsRepository defaultModelsRepository) {
    this.microServiceClientProvider = microServiceClientProvider;
    this.customerRepository = customerRepository;
    this.encryptionClientProvisioner = encryptionClientProvisioner;
    this.cloudStorageClientProvisioner = cloudStorageClientProvisioner;
    this.defaultModelsRepository = defaultModelsRepository;
  }

  public ProvisioningResult provisionCustomer(final CustomerProvisioningRequest request) {
    final int customerId = request.getId();
    final ProvisioningRun run = new ProvisioningRun();
    new Context(
            UUID.randomUUID().toString(), new UserContext(customerId, UserContext.SYSTEM.userId()))
        .run(
            () -> {
              run.step(
                  "encryption",
                  () ->
                      encryptionClientProvisioner.provision(
                          customerId, request.getDefaultServerId(ServerType.ENCRYPTION_KEY)));
              run.step(
                  "cloudstorage",
                  () ->
                      cloudStorageClientProvisioner.provision(
                          customerId, request.getDefaultServerId(ServerType.CLOUDSTORAGE_SERVER)));
              run.merge(
                  "catalog", () -> client(CatalogProvisioningService.class).provision(request));
              run.merge(
                  "connectors",
                  () -> client(ConnectorsProvisioningService.class).provision(request));
              run.merge(
                  "knowledge", () -> client(KnowledgeProvisioningService.class).provision(request));
              run.merge("agent", () -> client(AgentProvisioningService.class).provision(request));
              run.merge(
                  "scheduler", () -> client(SchedulerProvisioningService.class).provision(request));
              run.step("default-models", () -> saveDefaultModels(request));
              run.step("customer", () -> saveCustomer(request));
            });
    return run.result();
  }

  private void saveDefaultModels(final CustomerProvisioningRequest request) {
    if (request.getDefaultModels() != null) {
      defaultModelsRepository.save(request.getDefaultModels());
    }
  }

  private void saveCustomer(final CustomerProvisioningRequest request) {
    final Customer customer = new Customer();
    customer.setId(String.valueOf(request.getId()));
    customer.setName(request.getName());
    customerRepository.save(customer);
  }

  private <T extends ProvisioningService> T client(final Class<T> service) {
    return microServiceClientProvider.get(service);
  }
}
