package com.agentengine.internal;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.connectors.api.services.ConnectorsProvisioningService;
import com.agentengine.knowledge.api.services.KnowledgeProvisioningService;
import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.tenancy.AccessControlService;
import com.agentengine.tenancy.CustomerService;
import com.agentengine.tenancy.RoleService;
import com.agentengine.tenancy.TenancyProvisioningService;
import com.agentengine.tenancy.UserService;
import com.agentengine.tenancy.beans.Customer;
import com.agentengine.tenancy.beans.Role;
import com.agentengine.tenancy.beans.User;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.cloudstorage.CloudStorageClientProvisioner;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.agentengine.util.crypto.EncryptionClientProvisioner;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import com.agentengine.util.tasks.TaskStatus;
import com.agentengine.util.tenancy.SharingChange;
import com.agentengine.util.tenancy.StandardRole;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
              run.step("roles", () -> saveRoles(request));
              run.step("user", () -> saveUser(request));
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
    final CustomerService customerService = client(CustomerService.class);
    final Customer existing = customerService.getByDomain(domain);
    if (existing != null) {
      if (!existing.getId().equals(request.getId())) {
        throw new IllegalArgumentException(
            "Domain '" + domain + "' is already in use by customer '" + existing.getId() + "'");
      }
      existing.setName(request.getName());
      customerService.update(existing.getId(), existing);
      return;
    }
    final Customer customer = new Customer();
    customer.setId(request.getId());
    customer.setName(request.getName());
    customer.setDomain(domain);
    customerService.create(customer);
  }

  private void saveRoles(final CustomerProvisioningRequest request) {
    if (request.getRoles() == null) {
      return;
    }
    final RoleService roleService = client(RoleService.class);
    for (Role role : request.getRoles()) {
      Role existing = null;
      try {
        existing = roleService.getRole(role.getId());
      } catch (Exception e) {
        // Not found
      }
      if (existing != null) {
        existing.setAssetClassVsPermissions(role.getAssetClassVsPermissions());
        existing.setName(role.getName());
        existing.setStatus(TaskStatus.PENDING.name());
        existing.setStandard(role.isStandard());
        roleService.updateRole(role.getId(), existing);
      } else {
        roleService.createRole(role);
      }
    }
  }

  private void saveUser(final CustomerProvisioningRequest request) {
    if (request.getUser() == null) {
      return;
    }
    UserService userService = client(UserService.class);
    User user = request.getUser();
    User existing = null;
    try {
      existing = userService.get(user.getId());
    } catch (Exception e) {
      // Not found
    }
    if (existing != null) {
      existing.setPassword(user.getPassword());
      existing.setStatus(user.getStatus());
      existing.setUsername(user.getUsername());
      userService.update(user.getId(), existing);
    } else {
      userService.create(user);
    }

    AccessControlService accessControlService = client(AccessControlService.class);
    // Assign owner role to the user
    SharingChange change =
        SharingChange.onEveryAsset(
            Map.of(Principal.ofUser(user.getId()).toString(), Set.of(StandardRole.MANAGER)));
    accessControlService.updateSharing(List.of(change));
  }

  private static String normalizeDomain(final String value) {
    final String domain = value.trim().toLowerCase(Locale.ROOT);
    if (!HOSTNAME.matcher(domain).matches()) {
      throw new IllegalArgumentException("Invalid customer domain: '" + value + "'");
    }
    return domain;
  }

  private <T> T client(final Class<T> service) {
    return microServiceClientProvider.get(service);
  }
}
