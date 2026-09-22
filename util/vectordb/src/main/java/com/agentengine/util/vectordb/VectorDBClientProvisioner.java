package com.agentengine.util.vectordb;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.infra.InfraClientProvisioner;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sets up a customer's vector store: saves its client config and creates its collection. Runs in
 * the customer's context.
 */
@Singleton
public class VectorDBClientProvisioner extends InfraClientProvisioner {

  private static final int DEFAULT_VECTOR_SIZE = 768;

  private final InfraConfigService infraConfigService;

  @Inject
  public VectorDBClientProvisioner(
      final InfraConfigService infraConfigService, final ApplicationConfig applicationConfig) {
    super(applicationConfig);
    this.infraConfigService = infraConfigService;
  }

  public void provision(final VectorStore<?> store, final int customerId, final String serverId) {
    infraConfigService.save(
        VectorDbUtils.clientConfig(
            store.clientType(), customerId, resolvedServerId(ServerType.VECTOR_SERVER, serverId)));
    store.setup(DEFAULT_VECTOR_SIZE);
  }
}
