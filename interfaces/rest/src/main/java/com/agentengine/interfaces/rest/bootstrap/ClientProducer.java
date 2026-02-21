package com.agentengine.interfaces.rest.bootstrap;

import com.agentengine.engine.api.MicroServiceClientProvider;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.ModelService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.services.ToolService;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Produces gRPC client proxies for services that are not locally available.
 */
@Singleton
public class ClientProducer {

  @Produces
  @Singleton
  @DefaultBean
  public ToolService toolService(MicroServiceClientProvider provider) {
    return provider.get(ToolService.class);
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
