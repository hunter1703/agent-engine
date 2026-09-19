package com.agentengine.util.cloudstorage;

import com.agentengine.util.cloudstorage.oracle.OracleCloudStorage;
import com.agentengine.util.cloudstorage.s3.S3CloudStorage;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.InfraClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import jakarta.inject.Singleton;

@Singleton
public class CloudStorageServiceFactory extends InfraClientFactory<CloudStorageClientInfraConfig, CloudStorageServerInfraConfig, CloudStorageService> {

  protected CloudStorageServiceFactory(InfraConfigService infraConfigService, DistributedCacheManager cacheManager, InfraConfigService infraConfigService1) {
    super(infraConfigService, cacheManager, CloudStorageServerInfraConfig.TYPE);
  }

  @Override
  protected CloudStorageService create(final CloudStorageServerInfraConfig serverConfig) {
    final CloudStorageServerInfraConfig.Provider provider = serverConfig.providerType();
    final CloudStorageService cloudStorageService = switch (provider) {
      case ORACLE -> new OracleCloudStorage(serverConfig, infraConfigService);
      default -> new S3CloudStorage(serverConfig, infraConfigService);
    };
    return new DelegatingCloudStorageService(cloudStorageService);
  }
}
