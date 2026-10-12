package com.agentengine.connectors.api;

import com.agentengine.connectors.api.services.ConnectionAclService;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.connectors.api.services.ConnectorsProvisioningService;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class ClientProducer {

  @Produces
  @Singleton
  @DefaultBean
  public ConnectionService connectionService(final MicroServiceClientProvider provider) {
    return provider.get(ConnectionService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ConnectorService connectorService(final MicroServiceClientProvider provider) {
    return provider.get(ConnectorService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ConnectionAclService connectionAclService(final MicroServiceClientProvider provider) {
    return provider.get(ConnectionAclService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ConnectorsProvisioningService connectorsProvisioningService(
      final MicroServiceClientProvider provider) {
    return provider.get(ConnectorsProvisioningService.class);
  }
}
