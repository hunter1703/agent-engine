package com.agentengine.util.cloudstorage;

import com.agentengine.util.cloudstorage.oracle.OracleCloudStorage;
import com.agentengine.util.cloudstorage.s3.S3CloudStorage;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.InfraClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import jakarta.inject.Singleton;

@Singleton
public class CloudStorageServiceFactory
    extends InfraClientFactory<
        CloudStorageClientInfraConfig, CloudStorageServerInfraConfig, CloudStorageService> {

  private final ApplicationConfig applicationConfig;

  protected CloudStorageServiceFactory(
      final InfraConfigService infraConfigService,
      final DistributedCacheManager cacheManager,
      final ApplicationConfig applicationConfig) {
    super(infraConfigService, cacheManager, ServerType.CLOUDSTORAGE_SERVER);
    this.applicationConfig = applicationConfig;
  }

  public CloudStorageService get(final int customerId) {
    return get(
        getOrCreate(
            CloudStorageUtils.clientId(customerId),
            () ->
                CloudStorageUtils.clientConfig(
                    customerId,
                    ServerType.CLOUDSTORAGE_SERVER.defaultServerId(applicationConfig),
                    CloudStorageUtils.defaultBucket(customerId))));
  }

  @Override
  protected CloudStorageService create(final CloudStorageServerInfraConfig serverConfig) {
    final CloudStorageServerInfraConfig.Provider provider = serverConfig.providerType();
    final CloudStorageService cloudStorageService =
        switch (provider) {
          case ORACLE -> new OracleCloudStorage(serverConfig, infraConfigService);
          default -> new S3CloudStorage(serverConfig, infraConfigService);
        };
    return new DelegatingCloudStorageService(cloudStorageService);
  }
}
