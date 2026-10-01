package com.agentengine.tenancy.core.helpers;

import com.agentengine.tenancy.UserService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class UserPermissionHelper extends AssetPermissionHelper {

  @Inject
  public UserPermissionHelper(final UserService userService) {
    super(AssetClass.USER, userService);
  }
}
