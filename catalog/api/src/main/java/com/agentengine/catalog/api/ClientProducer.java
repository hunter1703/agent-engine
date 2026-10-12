package com.agentengine.catalog.api;

import com.agentengine.catalog.api.services.AgentAclService;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.AgentSessionAclService;
import com.agentengine.catalog.api.services.CatalogProvisioningService;
import com.agentengine.catalog.api.services.ModelAclService;
import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class ClientProducer {

  @Produces
  @Singleton
  @DefaultBean
  public AgentService agentService(final MicroServiceClientProvider provider) {
    return provider.get(AgentService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ModelService modelService(final MicroServiceClientProvider provider) {
    return provider.get(ModelService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public SessionService sessionService(final MicroServiceClientProvider provider) {
    return provider.get(SessionService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public CatalogProvisioningService catalogProvisioningService(
      final MicroServiceClientProvider provider) {
    return provider.get(CatalogProvisioningService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public AgentAclService agentAclService(final MicroServiceClientProvider provider) {
    return provider.get(AgentAclService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public AgentSessionAclService agentSessionAclService(final MicroServiceClientProvider provider) {
    return provider.get(AgentSessionAclService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ModelAclService modelAclService(final MicroServiceClientProvider provider) {
    return provider.get(ModelAclService.class);
  }
}
