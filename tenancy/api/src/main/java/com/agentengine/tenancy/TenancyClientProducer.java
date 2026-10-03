package com.agentengine.tenancy;

import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Produces gRPC clients of the tenancy services every permissioned service reaches, for any service
 * other than tenancy itself, where their implementations take over.
 */
@Singleton
public class TenancyClientProducer {

  @Produces
  @Singleton
  @DefaultBean
  public AccessControlService accessControlService(final MicroServiceClientProvider provider) {
    return provider.get(AccessControlService.class);
  }

  @Produces
  @Singleton
  @DefaultBean
  public UserService userService(final MicroServiceClientProvider provider) {
    return provider.get(UserService.class);
  }
}
