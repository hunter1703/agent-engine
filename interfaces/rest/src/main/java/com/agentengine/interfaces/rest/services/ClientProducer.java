package com.agentengine.interfaces.rest.services;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.agent.api.services.ToolCatalog;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.scheduler.api.runner.SchedulerService;
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
    public RuntimeService runtimeService(MicroServiceClientProvider provider) {
        return provider.get(RuntimeService.class);
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
    public SchedulerService schedulerService(MicroServiceClientProvider provider) {
        return provider.get(SchedulerService.class);
    }
}
