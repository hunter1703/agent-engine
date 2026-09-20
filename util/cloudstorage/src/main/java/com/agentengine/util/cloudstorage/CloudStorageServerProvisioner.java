package com.agentengine.util.cloudstorage;

import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraServerProvisioner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CloudStorageServerProvisioner extends InfraServerProvisioner<CloudStorageServerInfraConfig> {

  @Inject
  public CloudStorageServerProvisioner(final InfraConfigService infraConfigService) {
    super(infraConfigService);
  }
}
