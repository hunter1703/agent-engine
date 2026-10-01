package com.agentengine.tenancy.core.helpers;

import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentSessionPermissionHelper extends AssetPermissionHelper {

  @Inject
  public AgentSessionPermissionHelper(final SessionService sessionService) {
    super(AssetClass.AGENT_SESSION, sessionService);
  }
}
