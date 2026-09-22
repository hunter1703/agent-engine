package com.agentengine.util.ms.client;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.infra.InfraConfigService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MicroServiceProvisioner {

  private final InfraConfigService infraConfigService;

  @Inject
  public MicroServiceProvisioner(final InfraConfigService infraConfigService) {
    this.infraConfigService = infraConfigService;
  }

  public void provision(final int customerId, final String service, final String serverId) {
    infraConfigService.save(
        MicroServiceUtils.clientConfig(
            customerId,
            service,
            StringUtils.isNotBlank(serverId)
                ? serverId
                : MicroServiceUtils.defaultServerId(service)));
  }
}
