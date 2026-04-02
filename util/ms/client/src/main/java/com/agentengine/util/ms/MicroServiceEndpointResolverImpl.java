package com.agentengine.util.ms;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.mongodb.infra.InfraConfig;
import com.agentengine.util.mongodb.infra.InfraMongoRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves gRPC endpoints by looking up per-server config stored in the {@code MicroServiceConfig}
 * collection of the {@code INFRA} MongoDB database.
 *
 * <p>The server ID is read from the {@link MicroService#value()} annotation on the service
 * interface, so all services sharing the same server resolve to the same endpoint. Falls back to
 * {@code localhost:9000} when no config document is found.
 */
@Singleton
public class MicroServiceEndpointResolverImpl implements MicroServiceEndpointResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MicroServiceEndpointResolverImpl.class);
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 9000;

    private final InfraMongoRepository repository;

    @Inject
    public MicroServiceEndpointResolverImpl(final InfraMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public MicroServiceEndpoint resolve(final Class<?> serviceClass) {
        final MicroService annotation = serviceClass.getAnnotation(MicroService.class);
        if (annotation == null) {
            return new MicroServiceEndpoint(DEFAULT_HOST, DEFAULT_PORT);
        }
        final String serverId = annotation.value();
        final PaginatedResult<InfraConfig> results = repository.findByQuery(
                new Query().withFilter(Filters.eq(MicroServiceInfraConfig.FIELD_SERVER_ID, serverId)));
        if (CollectionUtils.isNotEmpty(results == null ? null : results.getItems())) {
            final MicroServiceInfraConfig config =
                    (MicroServiceInfraConfig) results.getItems().getFirst();
            LOG.debug("Resolved endpoint for server '{}': {}:{}", serverId, config.getHost(), config.getPort());
            return new MicroServiceEndpoint(config.getHost(), config.getPort());
        }
        LOG.debug("No config for server '{}', using defaults {}:{}", serverId, DEFAULT_HOST, DEFAULT_PORT);
        return new MicroServiceEndpoint(DEFAULT_HOST, DEFAULT_PORT);
    }
}
