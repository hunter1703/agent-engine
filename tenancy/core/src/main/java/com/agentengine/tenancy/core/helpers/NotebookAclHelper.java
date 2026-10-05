package com.agentengine.tenancy.core.helpers;

import com.agentengine.agent.api.services.NotebookAclService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NotebookAclHelper extends AssetAclHelper {

  @Inject
  public NotebookAclHelper(final NotebookAclService aclService) {
    super(AssetClass.NOTEBOOK, aclService);
  }
}
