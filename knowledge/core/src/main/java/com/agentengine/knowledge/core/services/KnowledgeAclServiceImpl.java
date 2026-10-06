package com.agentengine.knowledge.core.services;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.repository.KnowledgeDocumentStoreClientType;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.tenancy.AbstractAssetAclService;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class KnowledgeAclServiceImpl extends AbstractAssetAclService<Knowledge>
    implements com.agentengine.knowledge.api.services.KnowledgeAclService {

  @Inject
  public KnowledgeAclServiceImpl(final DocumentBackend documentBackend) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                KnowledgeDocumentStoreClientType.KNOWLEDGE, Knowledge.class)));
  }
}
