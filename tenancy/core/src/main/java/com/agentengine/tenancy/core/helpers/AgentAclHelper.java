package com.agentengine.tenancy.core.helpers;

import com.agentengine.catalog.api.services.AgentAclService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentAclHelper extends AssetAclHelper {

  @Inject
  public AgentAclHelper(final AgentAclService aclService) {
    super(AssetClass.AGENT, aclService);
  }
}
