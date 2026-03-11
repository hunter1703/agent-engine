package com.agentengine.util.ms;

import io.quarkus.arc.DefaultBean;
import jakarta.inject.Singleton;

/**
 * Resolves gRPC endpoints from system properties. Checks per-service properties first
 * ({@code agentengine.grpc.<ServiceName>.host/port}), then falls back to global defaults
 * ({@code agentengine.grpc.host/port}).
 *
 * <p>This bean is the default implementation and is overridden when a more specific resolver (e.g.,
 * infra-config-based) is available.
 */
@Singleton
@DefaultBean
public class SystemPropertyEndpointResolver implements MicroServiceEndpointResolver {

  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 9000;

  @Override
  public MicroServiceEndpoint resolve(Class<?> serviceClass) {
    String name = serviceClass.getSimpleName();
    String host =
        System.getProperty(
            "agentengine.grpc." + name + ".host",
            System.getProperty("agentengine.grpc.host", DEFAULT_HOST));
    int port =
        Integer.getInteger(
            "agentengine.grpc." + name + ".port",
            Integer.getInteger("agentengine.grpc.port", DEFAULT_PORT));
    return new MicroServiceEndpoint(host, port);
  }
}
