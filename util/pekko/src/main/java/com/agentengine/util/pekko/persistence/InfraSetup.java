package com.agentengine.util.pekko.persistence;

import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.InfraConfigService;
import org.apache.pekko.actor.setup.Setup;

public class InfraSetup extends Setup {

  private final InfraConfigService infraConfigService;
  private final DistributedCacheManager cacheManager;

  public InfraSetup(
      final InfraConfigService infraConfigService, final DistributedCacheManager cacheManager) {
    this.infraConfigService = infraConfigService;
    this.cacheManager = cacheManager;
  }

  public InfraConfigService infraConfigService() {
    return infraConfigService;
  }

  public DistributedCacheManager cacheManager() {
    return cacheManager;
  }
}
