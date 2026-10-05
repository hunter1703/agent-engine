package com.agentengine.tenancy.core.helpers;

import com.agentengine.tenancy.core.services.UserAclService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class UserAclHelper extends AssetAclHelper {

  @Inject
  public UserAclHelper(final UserAclService userAclService) {
    super(AssetClass.USER, userAclService);
  }
}
