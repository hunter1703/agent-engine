package com.agentengine.util.tenancy;

import com.agentengine.util.common.beans.Acl;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tenancy service's access lists: the role mappings it keeps, and the access lists and
 * permissions on every asset it calculates from them.
 */
public interface AclService {

  String PERMISSIONS_ON_EVERY_ASSET_CACHE = "PERMISSIONS_ON_EVERY_ASSET";

  /**
   * Each of {@code principals}' permissions on every asset, by principal: asset class to the names
   * of the permissions held on all of its assets, each with every permission it implies.
   */
  Map<String, Map<String, Set<String>>> getAssetClassPermissions(Collection<String> principals);

  /**
   * Deletes the role mappings of the assets, for assets their owning service has deleted; by the
   * system only.
   */
  void deleteAcls(String assetClass, Collection<String> assetIds);

  /**
   * Removes every role {@code principal} holds, on single assets and on every asset, for a
   * principal that is gone, such as a deleted session's; the access lists of the assets it held
   * roles on are recalculated without it. By the system only.
   */
  void forgetPrincipal(String principal);

  /**
   * Recalculates the asset's access list from all its role mappings and stores it, one version on
   * from the one the asset holds, only while the asset still holds that one. The role mappings of
   * an asset that no longer exists are deleted. By the system only.
   *
   * @throws com.agentengine.util.common.exception.StaleStateException when the asset's access list
   *     was written meanwhile
   */
  void recalculateAcl(String assetClass, String assetId);

  /**
   * Applies every change or, when the caller may not make one of them, none. Each asset's access
   * list is recalculated and stored on it once, however many changes touch it.
   */
  void updateSharing(List<SharingChange> changes);

  /**
   * Shares assets about to be created: adds the roles of {@code changes}, each adding roles on one
   * new asset, and returns each asset's access list calculated from them, by asset id, for the
   * asset to be stored with. The role mappings are written done, since the access list they make is
   * stored with the asset. By the system only.
   */
  Map<String, Acl> shareNewAssets(List<SharingChange> changes);
}
