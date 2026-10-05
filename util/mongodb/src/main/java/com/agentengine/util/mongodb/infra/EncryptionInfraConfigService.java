package com.agentengine.util.mongodb.infra;

import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.mongodb.mongo.MongoClientBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("encryptionInfraConfigService")
public class EncryptionInfraConfigService extends AbstractInfraConfigService {

  @Inject
  public EncryptionInfraConfigService(MongoClientBuilder mongoClientBuilder, DistributedCacheManager cacheManager) {
    super(mongoClientBuilder, cacheManager, null);
  }
}
