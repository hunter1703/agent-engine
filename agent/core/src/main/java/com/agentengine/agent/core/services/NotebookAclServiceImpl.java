package com.agentengine.agent.core.services;

import com.agentengine.agent.infra.notebook.Notebook;
import com.agentengine.util.agents.repository.AgentDocumentStoreClientType;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.tenancy.AbstractAssetAclService;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class NotebookAclServiceImpl extends AbstractAssetAclService<Notebook>
    implements com.agentengine.agent.api.services.NotebookAclService {

  @Inject
  public NotebookAclServiceImpl(final DocumentBackend documentBackend) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                AgentDocumentStoreClientType.AGENT, Notebook.class)));
  }
}
