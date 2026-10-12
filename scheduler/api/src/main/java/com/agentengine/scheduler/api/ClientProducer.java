package com.agentengine.scheduler.api;

import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.scheduler.api.runner.SchedulerService;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class ClientProducer {

  @Produces
  @Singleton
  @DefaultBean
  public SchedulerService schedulerService(final MicroServiceClientProvider provider) {
    return provider.get(SchedulerService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public SchedulerProvisioningService schedulerProvisioningService(
      final MicroServiceClientProvider provider) {
    return provider.get(SchedulerProvisioningService.class);
  }
}
