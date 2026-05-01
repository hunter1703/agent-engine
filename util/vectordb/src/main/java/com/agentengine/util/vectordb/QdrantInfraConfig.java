package com.agentengine.util.vectordb;

import com.agentengine.util.mongodb.infra.InfraConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/**
 * Infra config for the Qdrant vector database connection.
 *
 * <p>Stored in {@code INFRA.InfraConfig} under id {@code QDRANT:default} and seeded at
 * deploy time by {@code seed-infra-configs.sh}, which overrides {@link #host} to the
 * in-cluster Qdrant service name. Defaults target a local Qdrant instance.
 */
@BsonDiscriminator(value = "com.agentengine.util.vectordb.QdrantInfraConfig")
public class QdrantInfraConfig extends InfraConfig {

    public static final String TYPE = "qdrant";
    public static final String CATEGORY = "QDRANT";
    public static final String CONFIG_ID = "default";

    private String host = "localhost";
    private int grpcPort = 6334;

    public QdrantInfraConfig() {
        super(TYPE);
    }

    public String getHost() {
        return host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    public void setGrpcPort(final int grpcPort) {
        this.grpcPort = grpcPort;
    }
}
