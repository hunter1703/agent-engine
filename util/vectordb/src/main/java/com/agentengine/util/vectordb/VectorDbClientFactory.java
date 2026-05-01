package com.agentengine.util.vectordb;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton factory that owns the shared {@link QdrantClient} connection.
 *
 * <p>Reads connection details from {@link QdrantInfraConfig} via {@link InfraConfigService}
 * (backed by {@code INFRA.InfraConfig} in MongoDB), mirroring the pattern used by
 * {@code MongoClientFactory} and {@code LocalStackCloudStorageService}.
 */
@Singleton
public class VectorDbClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(VectorDbClientFactory.class);

    private final LazyLoader<QdrantClient> client;

    @Inject
    public VectorDbClientFactory(final InfraConfigService infraConfigService) {
        this.client = new LazyLoader<>(() -> {
            final QdrantInfraConfig config = infraConfigService.findById(QdrantInfraConfig.CATEGORY, QdrantInfraConfig.CONFIG_ID);
            final String host = config != null ? config.getHost() : "localhost";
            final int port = config != null ? config.getGrpcPort() : 6334;
            LOG.info("VectorDbClientFactory connecting to Qdrant at {}:{}", host, port);
            return new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());
        });
    }

    /** Returns the shared {@link QdrantClient}. */
    public QdrantClient getClient() {
        return client.get();
    }
}
