package com.agentengine.agent.core.services;

import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.knowledge.api.services.KnowledgeService;
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

    @Produces
    @Singleton
    @DefaultBean
    public SessionService sessionService(MicroServiceClientProvider provider) {
        return provider.get(SessionService.class);
    }

    @Produces
    @Singleton
    @DefaultBean
    public KnowledgeService knowledgeService(MicroServiceClientProvider provider) {
        return provider.get(KnowledgeService.class);
    }
}
