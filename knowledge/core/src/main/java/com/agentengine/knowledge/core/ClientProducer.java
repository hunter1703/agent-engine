package com.agentengine.knowledge.core;

import com.agentengine.catalog.api.services.ModelService;
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
    public ModelService modelService(MicroServiceClientProvider provider) {
        return provider.get(ModelService.class);
    }
}
