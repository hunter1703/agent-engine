package com.agentengine.tenancy;

import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class ClientProducer {

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
  public TenancyProvisioningService tenancyProvisioningService(
      final MicroServiceClientProvider provider) {
    return provider.get(TenancyProvisioningService.class);
  }
}
