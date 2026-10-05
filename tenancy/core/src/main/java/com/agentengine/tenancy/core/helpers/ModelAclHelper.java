package com.agentengine.tenancy.core.helpers;

import com.agentengine.catalog.api.services.ModelAclService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ModelAclHelper extends AssetAclHelper {

  @Inject
  public ModelAclHelper(final ModelAclService aclService) {
    super(AssetClass.MODEL, aclService);
  }
}
