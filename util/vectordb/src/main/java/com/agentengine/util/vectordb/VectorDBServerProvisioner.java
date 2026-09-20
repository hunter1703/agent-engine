package com.agentengine.util.vectordb;

import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraServerProvisioner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class VectorDBServerProvisioner extends InfraServerProvisioner<VectorServerInfraConfig> {

  @Inject
  public VectorDBServerProvisioner(final InfraConfigService infraConfigService) {
    super(infraConfigService);
  }
}
