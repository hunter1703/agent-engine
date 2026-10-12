package com.agentengine.knowledge.api;

import com.agentengine.knowledge.api.services.KnowledgeAclService;
import com.agentengine.knowledge.api.services.KnowledgeProvisioningService;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.knowledge.api.services.MediaService;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class ClientProducer {

  @Produces
  @Singleton
  @DefaultBean
  public KnowledgeService knowledgeService(final MicroServiceClientProvider provider) {
    return provider.get(KnowledgeService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public MediaService mediaService(final MicroServiceClientProvider provider) {
    return provider.get(MediaService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public KnowledgeAclService knowledgeAclService(final MicroServiceClientProvider provider) {
    return provider.get(KnowledgeAclService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public KnowledgeProvisioningService knowledgeProvisioningService(
      final MicroServiceClientProvider provider) {
    return provider.get(KnowledgeProvisioningService.class);
  }
}
