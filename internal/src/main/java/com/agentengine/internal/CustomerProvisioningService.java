package com.agentengine.internal;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.connectors.api.services.ConnectorsProvisioningService;
import com.agentengine.knowledge.api.services.KnowledgeProvisioningService;
import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.tenancy.Customer;
import com.agentengine.tenancy.CustomerRepository;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.tenancy.ProvisioningService;
import com.agentengine.util.cloudstorage.CloudStorageClientProvisioner;
import com.agentengine.util.cloudstorage.CloudStorageServerInfraConfig;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.crypto.EncryptionKeyInfraConfig;
import com.agentengine.util.crypto.EncryptionClientProvisioner;
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

  @Inject
  public CustomerProvisioningService(
      final MicroServiceClientProvider microServiceClientProvider,
      final CustomerRepository customerRepository,
      final EncryptionClientProvisioner encryptionClientProvisioner,
      final CloudStorageClientProvisioner cloudStorageClientProvisioner) {
    this.microServiceClientProvider = microServiceClientProvider;
    this.customerRepository = customerRepository;
    this.encryptionClientProvisioner = encryptionClientProvisioner;
    this.cloudStorageClientProvisioner = cloudStorageClientProvisioner;
  }

  public ProvisioningResult provisionCustomer(final CustomerProvisioningRequest request) {
    final int customerId = request.getId();
    final ProvisioningResult result = new ProvisioningResult();
    new Context(UUID.randomUUID().toString(), new UserContext(customerId, UserContext.SYSTEM.userId()))
        .run(
            () -> {
              result.step(
                  "encryption",
                  () -> encryptionClientProvisioner.provision(customerId, request.getServer(EncryptionKeyInfraConfig.TYPE, String.valueOf(customerId))));
              result.step(
                  "cloudstorage",
                  () -> cloudStorageClientProvisioner.provision(customerId, request.getServer(CloudStorageServerInfraConfig.TYPE, String.valueOf(customerId))));
              result.steps(
                  "catalog",
                  () -> client(CatalogProvisioningService.class).provision(customerId, request));
              result.steps(
                  "connectors",
                  () -> client(ConnectorsProvisioningService.class).provision(customerId, request));
              result.steps(
                  "knowledge",
                  () -> client(KnowledgeProvisioningService.class).provision(customerId, request));
              result.steps(
                  "agent",
                  () -> client(AgentProvisioningService.class).provision(customerId, request));
              result.steps(
                  "scheduler",
                  () -> client(SchedulerProvisioningService.class).provision(customerId, request));
              result.step("customer", () -> saveCustomer(request));
            });
    return result;
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
