package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.core.ConnectorsDocumentStoreClientType;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.tenancy.AbstractAssetAclService;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class ConnectionAclServiceImpl extends AbstractAssetAclService<Connection>
    implements com.agentengine.connectors.api.services.ConnectionAclService {

  @Inject
  public ConnectionAclServiceImpl(final DocumentBackend documentBackend) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                ConnectorsDocumentStoreClientType.CONNECTORS, Connection.class)));
  }
}
