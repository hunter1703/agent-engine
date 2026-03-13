package com.agentengine.util.ms;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves gRPC endpoints by looking up per-server config stored in the
 * {@code MicroServiceConfig} collection of the {@code INFRA} MongoDB database.
 *
 * <p>
 * The server ID is read from the {@link MicroService#value()} annotation on the
 * service interface, so all services sharing the same server resolve to the
 * same endpoint. Falls back to {@code localhost:9000} when no config document
 * is found.
 */
@Singleton
public class MicroServiceEndpointResolverImpl implements MicroServiceEndpointResolver {

  private static final Logger LOG = LoggerFactory.getLogger(MicroServiceEndpointResolverImpl.class);
  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 9000;

  private final MicroServiceRepository repository;

  @Inject
  public MicroServiceEndpointResolverImpl(final MicroServiceRepository repository) {
    this.repository = repository;
  }

  @Override
  public MicroServiceEndpoint resolve(final Class<?> serviceClass) {
    final MicroService annotation = serviceClass.getAnnotation(MicroService.class);
    if (annotation == null) {
      return new MicroServiceEndpoint(DEFAULT_HOST, DEFAULT_PORT);
    }
    final String serverId = annotation.value();
    final MicroServiceInfraConfig config = repository.findByServerId(serverId);
    if (config != null) {
      LOG.debug("Resolved endpoint for server '{}': {}:{}", serverId, config.getHost(), config.getPort());
      return new MicroServiceEndpoint(config.getHost(), config.getPort());
    }
    LOG.debug("No config for server '{}', using defaults {}:{}", serverId, DEFAULT_HOST, DEFAULT_PORT);
    return new MicroServiceEndpoint(DEFAULT_HOST, DEFAULT_PORT);
  }
}
