package com.agentengine.scheduler.core.services;

import com.agentengine.tenancy.UserService;
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
  public UserService userService(final MicroServiceClientProvider provider) {
    return provider.get(UserService.class);
  }
}
