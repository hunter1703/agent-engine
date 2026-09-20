package com.agentengine.util.vectordb;

import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.DefaultServers;
import com.agentengine.util.infra.InfraClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VectorDbClientFactory
    extends InfraClientFactory<VectorClientInfraConfig, VectorServerInfraConfig, QdrantClient> {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Logger LOG = LoggerFactory.getLogger(VectorDbClientFactory.class);

  private final DefaultServers defaultServers;

  @Inject
  public VectorDbClientFactory(
      final InfraConfigService infraConfigService,
      final DistributedCacheManager cacheManager,
      final DefaultServers defaultServers) {
    super(infraConfigService, cacheManager, VectorServerInfraConfig.TYPE);
    this.defaultServers = defaultServers;
  }

  public QdrantClient getClient(final VectorStoreClientType clientType, final Integer customerId) {
    return get(
        getOrCreate(
            VectorDbUtils.clientId(clientType.name(), customerId),
            () ->
                VectorDbUtils.clientConfig(
                    clientType,
                    customerId,
                    defaultServers.serverId(DefaultServers.VECTOR))));
  }

  @Override
  protected QdrantClient create(final VectorServerInfraConfig serverConfig) {
    LOG.info(
        "Connecting to Qdrant gRPC at {}:{}", serverConfig.getHost(), serverConfig.getGrpcPort());
    return new QdrantClient(
        QdrantGrpcClient.newBuilder(
                serverConfig.getHost(), serverConfig.getGrpcPort(), serverConfig.isTls())
            .withApiKey(serverConfig.getApiKey())
            .withTimeout(REQUEST_TIMEOUT)
            .build());
  }
}
