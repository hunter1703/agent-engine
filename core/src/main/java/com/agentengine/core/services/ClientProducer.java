package com.agentengine.core.services;

import com.agentengine.runtime.actor.SessionHistoryService;
import com.agentengine.runtime.actor.RuntimeService;
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

  @Produces
  @Singleton
  @DefaultBean
  public SessionHistoryService sessionHistory(MicroServiceClientProvider provider) {
    return provider.get(SessionHistoryService.class);
  }

}
