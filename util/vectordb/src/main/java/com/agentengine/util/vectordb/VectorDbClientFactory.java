package com.agentengine.util.vectordb;

import com.agentengine.util.common.config.ApplicationConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton factory that owns the shared {@link QdrantClient} connection.
 *
 * <p>Reads host and port from {@code ApplicationConfig} once and exposes a single
 * client instance for all {@link QdrantVectorStore} subclasses, mirroring the
 * pattern used by {@code MongoClientFactory}.
 */
@Singleton
public class VectorDbClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(VectorDbClientFactory.class);

    private static final String QDRANT_HOST_KEY = "knowledge.qdrant.host";
    private static final String QDRANT_PORT_KEY = "knowledge.qdrant.port";
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6334;

    private final QdrantClient client;

    @Inject
    public VectorDbClientFactory(final ApplicationConfig applicationConfig) {
        final String host = applicationConfig.getString(QDRANT_HOST_KEY, DEFAULT_HOST);
        final int port = parsePort(applicationConfig.getString(QDRANT_PORT_KEY));
        LOG.info("VectorDbClientFactory connecting to Qdrant at {}:{}", host, port);
        this.client =
                new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());
    }

    /** Returns the shared {@link QdrantClient}. */
    public QdrantClient getClient() {
        return client;
    }

    private static int parsePort(final String value) {
        if (value == null || value.isBlank()) return DEFAULT_PORT;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
