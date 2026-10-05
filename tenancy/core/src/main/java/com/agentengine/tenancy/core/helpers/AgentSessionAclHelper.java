package com.agentengine.tenancy.core.helpers;

import com.agentengine.catalog.api.services.AgentSessionAclService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentSessionAclHelper extends AssetAclHelper {

  @Inject
  public AgentSessionAclHelper(final AgentSessionAclService aclService) {
    super(AssetClass.AGENT_SESSION, aclService);
  }
}
