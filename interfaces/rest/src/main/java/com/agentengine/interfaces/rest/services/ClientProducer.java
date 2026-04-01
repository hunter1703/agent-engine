package com.agentengine.interfaces.rest.services;

import com.agentengine.core.api.services.AgentExecutionService;
import com.agentengine.core.api.services.AgentService;
import com.agentengine.core.api.services.ModelService;
import com.agentengine.core.api.services.SessionService;
import com.agentengine.runtime.api.services.ToolCatalog;
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
    public ToolCatalog toolCatalogService(MicroServiceClientProvider provider) {
        return provider.get(ToolCatalog.class);
    }

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
    public AgentExecutionService agentExecutionService(MicroServiceClientProvider provider) {
        return provider.get(AgentExecutionService.class);
    }

    @Produces
    @Singleton
    @DefaultBean
    public SessionService sessionService(MicroServiceClientProvider provider) {
        return provider.get(SessionService.class);
    }
}
