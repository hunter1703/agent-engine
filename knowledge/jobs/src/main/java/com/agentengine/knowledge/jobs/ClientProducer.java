package com.agentengine.knowledge.jobs;

import com.agentengine.knowledge.api.services.KnowledgeService;
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
}
