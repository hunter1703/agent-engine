package com.agentengine.catalog.core.services;

import com.agentengine.catalog.core.repository.CatalogDocumentStoreClientType;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.tenancy.AbstractAssetAclService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ModelAclServiceImpl extends AbstractAssetAclService<ModelConfig>
    implements com.agentengine.catalog.api.services.ModelAclService {

  @Inject
  public ModelAclServiceImpl(final DocumentBackend documentBackend) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                CatalogDocumentStoreClientType.CATALOG, ModelConfig.class)));
  }
}
