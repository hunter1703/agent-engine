package com.agentengine.tenancy.core.helpers;

import com.agentengine.agent.api.services.AgentScheduleAclService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentScheduleAclHelper extends AssetAclHelper {

  @Inject
  public AgentScheduleAclHelper(final AgentScheduleAclService aclService) {
    super(AssetClass.AGENT_SCHEDULE, aclService);
  }
}
