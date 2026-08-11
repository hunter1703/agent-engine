package com.agentengine.util.vectordb;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton factory that owns the shared {@link QdrantHttpClient} connection.
 *
 * <p>Reads connection details from {@link VectorDatabaseInfraConfig} via {@link InfraConfigService}
 * (backed by {@code INFRA.InfraConfig} in MongoDB), mirroring the pattern used by
 * {@code MongoClientFactory} and {@code LocalStackCloudStorageService}.
 *
 * <p>Uses HTTP REST API instead of gRPC to avoid protobuf classpath conflicts.
 */
@Singleton
public class VectorDbClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(VectorDbClientFactory.class);

    private final LazyLoader<QdrantHttpClient> client;

    @Inject
    public VectorDbClientFactory(final InfraConfigService infraConfigService) {
        this.client = new LazyLoader<>(() -> {
            final VectorDatabaseInfraConfig config = infraConfigService.findById(
                    VectorDatabaseInfraConfig.CATEGORY,
                    VectorDatabaseInfraConfig.TYPE,
                    VectorDatabaseInfraConfig.CONFIG_ID);
            final String host = config != null ? config.getHost() : "localhost";
            final int port = config != null ? config.getHttpPort() : 6333;
            final String apiKey = config != null ? config.getApiKey() : null;
            LOG.info("VectorDbClientFactory connecting to Qdrant HTTP at {}:{}", host, port);
            return new QdrantHttpClient(host, port, apiKey);
        });
    }

    /** Returns the shared {@link QdrantHttpClient}. */
    public QdrantHttpClient getClient() {
        return client.get();
    }
}
