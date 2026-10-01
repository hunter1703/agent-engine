package com.agentengine.tenancy.jobs;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class ClientProducer {

  @Produces
  @Singleton
  @DefaultBean
  public AccessControlService accessControlService(final MicroServiceClientProvider provider) {
    return provider.get(AccessControlService.class);
  }
}
