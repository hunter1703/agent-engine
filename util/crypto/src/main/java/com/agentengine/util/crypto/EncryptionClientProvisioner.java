package com.agentengine.util.crypto;

import com.agentengine.util.infra.ServerType;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraClientProvisioner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Sets up a customer's encryption: a client on the key the request names, or the default. */
@Singleton
public class EncryptionClientProvisioner extends InfraClientProvisioner {

  private final InfraConfigService infraConfigService;

  @Inject
  public EncryptionClientProvisioner(
          final InfraConfigService infraConfigService, final ApplicationConfig applicationConfig) {
      super(applicationConfig);
      this.infraConfigService = infraConfigService;
  }

  public void provision(final int customerId, final String serverId) {
    infraConfigService.save(
        EncryptionUtils.clientConfig(
            customerId, resolvedServerId(ServerType.ENCRYPTION_KEY, serverId)));
  }
}
