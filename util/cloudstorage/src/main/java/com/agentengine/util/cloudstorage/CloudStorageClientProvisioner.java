package com.agentengine.util.cloudstorage;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraClientProvisioner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CloudStorageClientProvisioner extends InfraClientProvisioner {

  private final InfraConfigService infraConfigService;
  private final CloudStorageServiceFactory storageFactory;

  @Inject
  public CloudStorageClientProvisioner(
          final InfraConfigService infraConfigService,
          final CloudStorageServiceFactory storageFactory, ApplicationConfig applicationConfig) {
      super(applicationConfig);
      this.infraConfigService = infraConfigService;
    this.storageFactory = storageFactory;
  }

  public void provision(final int customerId, final String serverId) {
    final String bucket = CloudStorageUtils.defaultBucket(customerId);
    infraConfigService.save(
        CloudStorageUtils.clientConfig(
            customerId, resolvedServerId(CloudStorageServerInfraConfig.TYPE, serverId), bucket));
    storageFactory.get(customerId).ensureBucket(bucket);
  }
}
