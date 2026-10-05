package com.agentengine.tenancy.core.helpers;

import com.agentengine.tenancy.AssetAclService;
import com.agentengine.util.common.beans.Acl;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Tenancy's side of one permissioned asset class: reads and stores the access lists of its assets
 * through the domain service that keeps them.
 */
public abstract class AssetAclHelper {

  private final String assetClass;
  private final AssetAclService assetAclService;

  protected AssetAclHelper(final String assetClass, final AssetAclService assetAclService) {
    this.assetClass = assetClass;
    this.assetAclService = assetAclService;
  }

  public final String assetClass() {
    return assetClass;
  }

  public final Map<String, Acl> getAcls(final Collection<String> assetIds) {
    return assetAclService.getAcls(assetClass, assetIds);
  }

  public final Set<String> applyAcls(final Map<String, Acl> assetIdVsAcl) {
    return assetAclService.applyAcls(assetClass, assetIdVsAcl);
  }
}
