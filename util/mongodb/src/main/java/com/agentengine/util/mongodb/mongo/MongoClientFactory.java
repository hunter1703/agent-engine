package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.common.repository.DocumentStoreClientType;
import com.agentengine.util.crypto.EncryptionService;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.ClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.mongodb.infra.MongoClientInfraConfig;
import com.agentengine.util.mongodb.infra.MongoServerInfraConfig;
import com.mongodb.client.MongoClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MongoClientFactory
    extends ClientFactory<MongoClientInfraConfig, MongoServerInfraConfig, MongoClient> {
  private final String defaultServerId;
  private final MongoClientBuilder mongoClientBuilder;
  private final EncryptionService encryptionService;

  @Inject
  public MongoClientFactory(
      InfraConfigService infraConfigService,
      DistributedCacheManager cacheManager,
      ApplicationConfig applicationConfig,
      MongoClientBuilder mongoClientBuilder,
      EncryptionService encryptionService) {
    super(infraConfigService, cacheManager, ServerType.MONGO_SERVER);
    this.defaultServerId = ServerType.MONGO_SERVER.defaultServerId(applicationConfig);
    this.mongoClientBuilder = mongoClientBuilder;
    this.encryptionService = encryptionService;
  }

  public MongoClient getClient(final DocumentStoreClientType clientType, final String customerId) {
    return get(
        getOrCreate(
            MongoUtils.clientId(clientType.name(), customerId),
            () -> MongoUtils.clientConfig(clientType.name(), customerId, defaultServerId)));
  }

  @Override
  protected MongoClient create(final MongoServerInfraConfig serverConfig) {
    return mongoClientBuilder.get(serverConfig.getUri(), encryptionService);
  }
}
