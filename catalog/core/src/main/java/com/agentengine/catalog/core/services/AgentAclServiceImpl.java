package com.agentengine.catalog.core.services;

import com.agentengine.catalog.core.repository.CatalogDocumentStoreClientType;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.tenancy.AbstractAssetAclService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentAclServiceImpl extends AbstractAssetAclService<BaseAgentConfig>
    implements com.agentengine.catalog.api.services.AgentAclService {

  @Inject
  public AgentAclServiceImpl(final DocumentBackend documentBackend) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                CatalogDocumentStoreClientType.CATALOG, BaseAgentConfig.class)));
  }
}
