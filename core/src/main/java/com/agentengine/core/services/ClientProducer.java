package com.agentengine.core.services;

import com.agentengine.core.api.services.AgentService;
import com.agentengine.core.api.services.ModelService;
import com.agentengine.runtime.actor.services.RuntimeService;
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
  public RuntimeService runtimeService(MicroServiceClientProvider provider) {
    return provider.get(RuntimeService.class);
  }

}
