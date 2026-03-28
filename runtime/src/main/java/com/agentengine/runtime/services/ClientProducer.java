package com.agentengine.runtime.services;

import com.agentengine.core.api.services.AgentService;
import com.agentengine.core.api.services.ModelService;
import com.agentengine.util.ms.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Produces gRPC client proxies for services that are not locally available. */
@Singleton
public class ClientProducer {

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

}
