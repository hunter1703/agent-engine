package com.agentengine.util.crypto;

import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraServerProvisioner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class EncryptionServerProvisioner extends InfraServerProvisioner<EncryptionKeyInfraConfig> {

  @Inject
  public EncryptionServerProvisioner(final InfraConfigService infraConfigService) {
    super(infraConfigService);
  }
}
