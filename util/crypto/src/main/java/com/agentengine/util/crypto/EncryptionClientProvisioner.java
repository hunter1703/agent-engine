package com.agentengine.util.crypto;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.InfraClientProvisioner;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningRequest;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
import com.agentengine.util.infra.provisioning.ProvisioningService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sets up a customer's encryption: a client on the key the request names, or the default. Also a
 * {@link ProvisioningService} so {@code CustomerProvisioningService}/
 * {@code EnvironmentProvisioningService} discover and call it the same generic way as every
 * other provisioning step, for the context's current customer (the system, at environment level).
 */
@Singleton
public class EncryptionClientProvisioner extends InfraClientProvisioner
    implements ProvisioningService {

  private final InfraConfigService infraConfigService;

  @Inject
  public EncryptionClientProvisioner(
      final InfraConfigService infraConfigService, final ApplicationConfig applicationConfig) {
    super(applicationConfig);
    this.infraConfigService = infraConfigService;
  }

  @Override
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    return provisionForCurrentCustomer(request);
  }

  @Override
  public ProvisioningResult provision(final ProvisioningRequest request) {
    return provisionForCurrentCustomer(request);
  }

  public void provision(final String customerId, final String serverId) {
    infraConfigService.save(
        EncryptionUtils.clientConfig(
            customerId, resolvedServerId(ServerType.ENCRYPTION_KEY, serverId)));
  }

  private ProvisioningResult provisionForCurrentCustomer(final ProvisioningRequest request) {
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "encryption",
        () ->
            provision(
                Context.requireCustomerId(), request.getDefaultServerId(ServerType.ENCRYPTION_KEY)));
    return run.result();
  }
}
