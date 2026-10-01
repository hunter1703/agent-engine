package com.agentengine.tenancy;

import com.agentengine.util.common.beans.Acl;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * What tenancy asks of the service that keeps a class of assets. The domain service of each
 * permissioned asset class provides it.
 */
public interface AssetPermissionService {

  /** The stored access lists of the assets among {@code assetIds} that exist. */
  Map<String, Acl> getAcls(String assetClass, Collection<String> assetIds);

  /** Stores each access list newer than the asset's own; returns the ids of the assets updated. */
  Set<String> applyAcls(String assetClass, Map<String, Acl> assetIdVsAcl);
}
