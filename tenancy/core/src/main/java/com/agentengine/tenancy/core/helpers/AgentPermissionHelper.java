package com.agentengine.tenancy.core.helpers;

import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentPermissionHelper extends AssetPermissionHelper {

  @Inject
  public AgentPermissionHelper(final AgentService agentService) {
    super(AssetClass.AGENT, agentService);
  }
}
