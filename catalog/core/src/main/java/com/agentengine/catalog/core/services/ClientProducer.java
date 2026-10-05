package com.agentengine.catalog.core.services;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.tenancy.AccessControlService;
import com.agentengine.tenancy.CustomerService;
import com.agentengine.tenancy.PermissionService;
import com.agentengine.tenancy.RoleService;
import com.agentengine.tenancy.TenancyProvisioningService;
import com.agentengine.tenancy.UserService;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Produces gRPC client proxies for services that are not locally available. */
@Singleton
public class ClientProducer {

  @Produces
  @Singleton
  @DefaultBean
  public RuntimeService runtimeService(MicroServiceClientProvider provider) {
    return provider.get(RuntimeService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public AccessControlService accessControlService(final MicroServiceClientProvider provider) {
    return provider.get(AccessControlService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public PermissionService permissionService(final MicroServiceClientProvider provider) {
    return provider.get(PermissionService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public RoleService roleService(final MicroServiceClientProvider provider) {
    return provider.get(RoleService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public UserService userService(final MicroServiceClientProvider provider) {
    return provider.get(UserService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public CustomerService customerService(final MicroServiceClientProvider provider) {
    return provider.get(CustomerService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public TenancyProvisioningService tenancyProvisioningService(
      final MicroServiceClientProvider provider) {
    return provider.get(TenancyProvisioningService.class);
  }
}
