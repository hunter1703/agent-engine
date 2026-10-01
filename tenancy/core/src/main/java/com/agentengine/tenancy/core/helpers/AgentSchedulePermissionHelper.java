package com.agentengine.tenancy.core.helpers;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentSchedulePermissionHelper extends AssetPermissionHelper {

  @Inject
  public AgentSchedulePermissionHelper(final RuntimeService runtimeService) {
    super(AssetClass.AGENT_SCHEDULE, runtimeService);
  }
}
