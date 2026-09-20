package com.agentengine.util.ms.client;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraClientProvisioner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MicroServiceProvisioner extends InfraClientProvisioner {

  private final InfraConfigService infraConfigService;

  @Inject
  public MicroServiceProvisioner(final InfraConfigService infraConfigService, ApplicationConfig applicationConfig) {
      super(applicationConfig);
      this.infraConfigService = infraConfigService;
  }

  public void provision(final int customerId, final String service, final String serverId) {
    infraConfigService.save(MicroServiceUtils.clientConfig(customerId, service, serverId));
  }
}
