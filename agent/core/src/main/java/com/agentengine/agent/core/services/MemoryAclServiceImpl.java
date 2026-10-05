package com.agentengine.agent.core.services;

import com.agentengine.agent.core.memory.Memory;
import com.agentengine.util.agents.repository.AgentDocumentStoreClientType;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.tenancy.AbstractAssetAclService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkus.arc.Unremovable;

@Singleton
@Unremovable
public class MemoryAclServiceImpl extends AbstractAssetAclService<Memory>
    implements com.agentengine.agent.api.services.MemoryAclService {

  @Inject
  public MemoryAclServiceImpl(final DocumentBackend documentBackend) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(AgentDocumentStoreClientType.AGENT, Memory.class)));
  }
}
