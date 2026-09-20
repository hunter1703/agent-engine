package com.agentengine.util.ms.client;

import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraServerProvisioner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MicroServiceServerProvisioner extends InfraServerProvisioner<MicroServiceServerInfraConfig> {

  @Inject
  public MicroServiceServerProvisioner(final InfraConfigService infraConfigService) {
    super(infraConfigService);
  }
}
