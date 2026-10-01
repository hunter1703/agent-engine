package com.agentengine.tenancy.core.helpers;

import com.agentengine.tenancy.AssetPermissionService;
import com.agentengine.util.common.beans.Acl;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Tenancy's side of one permissioned asset class: reads and stores the access lists of its assets
 * through the domain service that keeps them.
 */
public abstract class AssetPermissionHelper {

  private final String assetClass;
  private final AssetPermissionService assetPermissionService;

  protected AssetPermissionHelper(
      final String assetClass, final AssetPermissionService assetPermissionService) {
    this.assetClass = assetClass;
    this.assetPermissionService = assetPermissionService;
  }

  public final String assetClass() {
    return assetClass;
  }

  public final Map<String, Acl> getAcls(final Collection<String> assetIds) {
    return assetPermissionService.getAcls(assetClass, assetIds);
  }

  public final Set<String> applyAcls(final Map<String, Acl> assetIdVsAcl) {
    return assetPermissionService.applyAcls(assetClass, assetIdVsAcl);
  }
}
