package com.agentengine.tenancy.core.services;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.knowledge.api.services.KnowledgeService;
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
  public RuntimeService runtimeService(final MicroServiceClientProvider provider) {
    return provider.get(RuntimeService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public KnowledgeService knowledgeService(final MicroServiceClientProvider provider) {
    return provider.get(KnowledgeService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ConnectionService connectionService(final MicroServiceClientProvider provider) {
    return provider.get(ConnectionService.class);
  }
}
