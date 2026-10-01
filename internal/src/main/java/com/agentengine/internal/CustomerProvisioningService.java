package com.agentengine.internal;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.connectors.api.services.ConnectorsProvisioningService;
import com.agentengine.knowledge.api.services.KnowledgeProvisioningService;
import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.tenancy.CustomerService;
import com.agentengine.tenancy.TenancyProvisioningService;
import com.agentengine.tenancy.beans.Customer;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.cloudstorage.CloudStorageClientProvisioner;
import com.agentengine.util.context.Context;
import com.agentengine.util.crypto.EncryptionClientProvisioner;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
import com.agentengine.util.infra.provisioning.ProvisioningService;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Locale;
import java.util.regex.Pattern;

@Singleton
public class CustomerProvisioningService {

  private static final Pattern HOSTNAME =
      Pattern.compile("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*");

  private final MicroServiceClientProvider microServiceClientProvider;
  private final EncryptionClientProvisioner encryptionClientProvisioner;
  private final CloudStorageClientProvisioner cloudStorageClientProvisioner;
  private final DefaultModelsRepository defaultModelsRepository;

  @Inject
  public CustomerProvisioningService(
      final MicroServiceClientProvider microServiceClientProvider,
      final EncryptionClientProvisioner encryptionClientProvisioner,
      final CloudStorageClientProvisioner cloudStorageClientProvisioner,
      final DefaultModelsRepository defaultModelsRepository) {
    this.microServiceClientProvider = microServiceClientProvider;
    this.encryptionClientProvisioner = encryptionClientProvisioner;
    this.cloudStorageClientProvisioner = cloudStorageClientProvisioner;
    this.defaultModelsRepository = defaultModelsRepository;
  }

  public ProvisioningResult provisionCustomer(final CustomerProvisioningRequest request) {
    final String customerId = request.getId();
    final String domain = normalizeDomain(request.getDomain());
    final ProvisioningRun run = new ProvisioningRun();
    Context.asSystemUser(customerId)
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
              run.merge(
                  "tenancy", () -> client(TenancyProvisioningService.class).provision(request));
              run.step("default-models", () -> saveDefaultModels(request));
              run.step("customer", () -> saveCustomer(request, domain));
            });
    return run.result();
  }

  private void saveDefaultModels(final CustomerProvisioningRequest request) {
    if (request.getDefaultModels() != null) {
      // Provisioning sets the customer's default models to those of the request, whatever they
      // were.
      defaultModelsRepository.saveIgnoringVersion(request.getDefaultModels());
    }
  }

  private void saveCustomer(final CustomerProvisioningRequest request, final String domain) {
    final Customer customer = new Customer();
    customer.setId(request.getId());
    customer.setName(request.getName());
    customer.setDomain(domain);
    microServiceClientProvider.get(CustomerService.class).create(customer);
  }

  private static String normalizeDomain(final String value) {
    final String domain = value.trim().toLowerCase(Locale.ROOT);
    if (!HOSTNAME.matcher(domain).matches()) {
      throw new IllegalArgumentException("Invalid customer domain: '" + value + "'");
    }
    return domain;
  }

  private <T extends ProvisioningService> T client(final Class<T> service) {
    return microServiceClientProvider.get(service);
  }
}
