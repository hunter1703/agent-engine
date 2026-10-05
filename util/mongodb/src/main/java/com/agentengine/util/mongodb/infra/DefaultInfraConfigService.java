package com.agentengine.util.mongodb.infra;

import com.agentengine.util.crypto.EncryptionService;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.mongodb.mongo.MongoClientBuilder;
import io.quarkus.arc.DefaultBean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@DefaultBean
public class DefaultInfraConfigService extends AbstractInfraConfigService {

  @Inject
  public DefaultInfraConfigService(
      MongoClientBuilder mongoClientBuilder,
      DistributedCacheManager cacheManager,
      EncryptionService encryptionService) {
    super(mongoClientBuilder, cacheManager, encryptionService);
  }
}
