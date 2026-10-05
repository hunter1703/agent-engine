package com.agentengine.tenancy.core.helpers;

import com.agentengine.connectors.api.services.ConnectionAclService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ConnectionAclHelper extends AssetAclHelper {

  @Inject
  public ConnectionAclHelper(final ConnectionAclService aclService) {
    super(AssetClass.CONNECTION, aclService);
  }
}
