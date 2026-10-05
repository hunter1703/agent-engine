package com.agentengine.catalog.core.services;

import com.agentengine.catalog.core.repository.CatalogDocumentStoreClientType;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.tenancy.AbstractAssetAclService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentSessionAclServiceImpl extends AbstractAssetAclService<AgentSession>
    implements com.agentengine.catalog.api.services.AgentSessionAclService {

  @Inject
  public AgentSessionAclServiceImpl(final DocumentBackend documentBackend) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                CatalogDocumentStoreClientType.CATALOG, AgentSession.class)));
  }
}
