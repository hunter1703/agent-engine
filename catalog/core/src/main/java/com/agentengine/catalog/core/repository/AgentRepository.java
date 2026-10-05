package com.agentengine.catalog.core.repository;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.tenancy.AbstractPermissionedRepository;
import com.agentengine.util.tenancy.PermissionChecker;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Startup
public class AgentRepository extends AbstractPermissionedRepository<BaseAgentConfig> {
  @Inject
  public AgentRepository(
      final DocumentBackend documentBackend,
      final ValidationService validationService,
      final PermissionChecker permissionChecker,
      final AccessControlService accessControlService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                CatalogDocumentStoreClientType.CATALOG, BaseAgentConfig.class, "AgentConfig")),
        validationService,
        permissionChecker,
        accessControlService);
  }
}
