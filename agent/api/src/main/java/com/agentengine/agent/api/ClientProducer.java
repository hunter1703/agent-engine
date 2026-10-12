package com.agentengine.agent.api;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.agent.api.services.AgentScheduleAclService;
import com.agentengine.agent.api.services.CommunityExpertsService;
import com.agentengine.agent.api.services.MemoryAclService;
import com.agentengine.agent.api.services.NotebookAclService;
import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.agent.api.services.ToolCatalog;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class ClientProducer {

  @Produces
  @Singleton
  @DefaultBean
  public CommunityExpertsService communityExpertsService(
      final MicroServiceClientProvider provider) {
    return provider.get(CommunityExpertsService.class);
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
  public ToolCatalog toolCatalog(final MicroServiceClientProvider provider) {
    return provider.get(ToolCatalog.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public AgentProvisioningService agentProvisioningService(
      final MicroServiceClientProvider provider) {
    return provider.get(AgentProvisioningService.class);
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
  public AgentScheduleAclService agentScheduleAclService(
      final MicroServiceClientProvider provider) {
    return provider.get(AgentScheduleAclService.class);
  }
}
