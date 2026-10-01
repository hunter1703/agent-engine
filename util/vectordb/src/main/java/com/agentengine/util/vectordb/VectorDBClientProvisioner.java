package com.agentengine.util.vectordb;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.infra.InfraClientProvisioner;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sets up a customer's vector store: saves its client config and creates its collections. Runs in
 * the customer's context.
 */
@Singleton
public class VectorDBClientProvisioner extends InfraClientProvisioner {

  private final InfraConfigService infraConfigService;
  private final Instance<VectorBackend> vectorBackends;

  @Inject
  public VectorDBClientProvisioner(
      final InfraConfigService infraConfigService,
      final ApplicationConfig applicationConfig,
      @Any final Instance<VectorBackend> vectorBackends) {
    super(applicationConfig);
    this.infraConfigService = infraConfigService;
    this.vectorBackends = vectorBackends;
  }

  /**
   * Saves the client config of the {@code clientType} store of {@code customerId} and creates its
   * collections.
   */
  public void provision(
      final VectorStoreClientType clientType, final String customerId, final String serverId) {
    infraConfigService.save(
        VectorDbUtils.clientConfig(
            clientType, customerId, resolvedServerId(ServerType.VECTOR_SERVER, serverId)));
    vectorBackends.forEach(vectorBackend -> vectorBackend.setup(clientType, customerId));
  }
}
