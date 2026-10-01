package com.agentengine.connectors.core;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.tenancy.AccessControlService;
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
public class ConnectionRepository extends AbstractPermissionedRepository<Connection> {

  @Inject
  public ConnectionRepository(
      final DocumentBackend documentBackend,
      final ValidationService validationService,
      final PermissionChecker permissionChecker,
      final AccessControlService accessControlService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                ConnectorsDocumentStoreClientType.CONNECTORS, Connection.class)),
        validationService,
        permissionChecker,
        accessControlService);
  }
}
