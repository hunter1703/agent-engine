package com.agentengine.util.cloudstorage;

import com.agentengine.util.cloudstorage.oracle.OracleCloudStorage;
import com.agentengine.util.cloudstorage.s3.S3CloudStorage;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.DefaultServers;
import com.agentengine.util.infra.InfraClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import jakarta.inject.Singleton;

@Singleton
public class CloudStorageServiceFactory extends InfraClientFactory<CloudStorageClientInfraConfig, CloudStorageServerInfraConfig, CloudStorageService> {

  private final DefaultServers defaultServers;

  protected CloudStorageServiceFactory(
      final InfraConfigService infraConfigService,
      final DistributedCacheManager cacheManager,
      final DefaultServers defaultServers) {
    super(infraConfigService, cacheManager, CloudStorageServerInfraConfig.TYPE);
    this.defaultServers = defaultServers;
  }

  public CloudStorageService get(final int customerId) {
    return get(
        getOrCreate(
            CloudStorageUtils.clientId(customerId),
            () ->
                CloudStorageUtils.clientConfig(
                    customerId,
                    defaultServers.serverId(DefaultServers.CLOUDSTORAGE),
                    CloudStorageUtils.defaultBucket(customerId))));
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
