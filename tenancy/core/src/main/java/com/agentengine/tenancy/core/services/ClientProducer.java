package com.agentengine.tenancy.core.services;

import com.agentengine.agent.api.services.AgentScheduleAclService;
import com.agentengine.agent.api.services.MemoryAclService;
import com.agentengine.agent.api.services.NotebookAclService;
import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.catalog.api.services.AgentAclService;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.AgentSessionAclService;
import com.agentengine.catalog.api.services.ModelAclService;
import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.connectors.api.services.ConnectionAclService;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.knowledge.api.services.KnowledgeAclService;
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

  @Produces
  @Singleton
  @DefaultBean
  public ConnectionAclService connectionAclService(final MicroServiceClientProvider provider) {
    return provider.get(ConnectionAclService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public AgentSessionAclService agentsessionAclService(final MicroServiceClientProvider provider) {
    return provider.get(AgentSessionAclService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public ModelAclService modelAclService(final MicroServiceClientProvider provider) {
    return provider.get(ModelAclService.class);
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
  public MemoryAclService memoryAclService(final MicroServiceClientProvider provider) {
    return provider.get(MemoryAclService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public NotebookAclService notebookAclService(final MicroServiceClientProvider provider) {
    return provider.get(NotebookAclService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public AgentScheduleAclService agentscheduleAclService(
      final MicroServiceClientProvider provider) {
    return provider.get(AgentScheduleAclService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public KnowledgeAclService knowledgeAclService(final MicroServiceClientProvider provider) {
    return provider.get(KnowledgeAclService.class);
  }
}
