package com.agentengine.tenancy.core.helpers;

import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ConnectionPermissionHelper extends AssetPermissionHelper {

  @Inject
  public ConnectionPermissionHelper(final ConnectionService connectionService) {
    super(AssetClass.CONNECTION, connectionService);
  }
}
