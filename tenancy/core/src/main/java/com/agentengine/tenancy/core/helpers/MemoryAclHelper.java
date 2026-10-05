package com.agentengine.tenancy.core.helpers;

import com.agentengine.agent.api.services.MemoryAclService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MemoryAclHelper extends AssetAclHelper {

  @Inject
  public MemoryAclHelper(final MemoryAclService aclService) {
    super(AssetClass.MEMORY, aclService);
  }
}
