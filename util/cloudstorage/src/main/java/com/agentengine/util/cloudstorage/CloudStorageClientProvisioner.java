package com.agentengine.util.cloudstorage;

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
 * Also a {@link ProvisioningService} so {@code CustomerProvisioningService} discovers and calls
 * it the same generic way as every other provisioning step. Customer-only: there is no bucket
 * for the system pseudo-customer, so {@link #provisionEnvironment} is a no-op, unlike {@link
 * com.agentengine.util.crypto.EncryptionClientProvisioner}, which genuinely needs both.
 */
@Singleton
public class CloudStorageClientProvisioner extends InfraClientProvisioner
    implements ProvisioningService {

  private final InfraConfigService infraConfigService;
  private final CloudStorageServiceFactory storageFactory;

  @Inject
  public CloudStorageClientProvisioner(
      final InfraConfigService infraConfigService,
      final CloudStorageServiceFactory storageFactory,
      ApplicationConfig applicationConfig) {
    super(applicationConfig);
    this.infraConfigService = infraConfigService;
    this.storageFactory = storageFactory;
  }

  @Override
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    return provisionForCurrentCustomer(request);
  }

  @Override
  public ProvisioningResult provision(final ProvisioningRequest request) {
    return provisionForCurrentCustomer(request);
  }

  private ProvisioningResult provisionForCurrentCustomer(final ProvisioningRequest request) {
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "cloudstorage",
        () ->
            provision(
                Context.requireCustomerId(),
                request.getDefaultServerId(ServerType.CLOUDSTORAGE_SERVER)));
    return run.result();
  }

  private void provision(final String customerId, final String serverId) {
    final String bucket = CloudStorageUtils.defaultBucket(customerId);
    infraConfigService.save(
            CloudStorageUtils.clientConfig(
                    customerId, resolvedServerId(ServerType.CLOUDSTORAGE_SERVER, serverId), bucket));
    storageFactory.get(customerId).ensureBucket(bucket);
  }
}
