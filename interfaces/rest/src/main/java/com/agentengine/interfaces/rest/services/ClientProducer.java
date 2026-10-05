package com.agentengine.interfaces.rest.services;
import com.agentengine.tenancy.AccessControlService;
import com.agentengine.tenancy.PermissionService;
import com.agentengine.tenancy.RoleService;
import com.agentengine.tenancy.UserService;
import com.agentengine.tenancy.TenancyProvisioningService;


import com.agentengine.agent.api.services.ToolCatalog;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.tenancy.CustomerService;
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
  public ToolCatalog toolCatalogService(MicroServiceClientProvider provider) {
    return provider.get(ToolCatalog.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public AgentService agentService(MicroServiceClientProvider provider) {
    return provider.get(AgentService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ModelService modelService(MicroServiceClientProvider provider) {
    return provider.get(ModelService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public SessionService sessionService(MicroServiceClientProvider provider) {
    return provider.get(SessionService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ConnectionService connectionService(MicroServiceClientProvider provider) {
    return provider.get(ConnectionService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ConnectorService connectorService(MicroServiceClientProvider provider) {
    return provider.get(ConnectorService.class);
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
  public UserService userService(final MicroServiceClientProvider provider) {
    return provider.get(UserService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public TenancyProvisioningService tenancyProvisioningService(final MicroServiceClientProvider provider) {
    return provider.get(TenancyProvisioningService.class);
  }
}
