package com.agentengine.tenancy.core.helpers;

import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ModelPermissionHelper extends AssetPermissionHelper {

  @Inject
  public ModelPermissionHelper(final ModelService modelService) {
    super(AssetClass.MODEL, modelService);
  }
}
