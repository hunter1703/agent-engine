package com.agentengine.util.tenancy;

import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.beans.BaseEntity;
import java.util.function.Supplier;

/** Decides whether the current context holds a permission, on one entity or a whole class. */
public interface PermissionChecker {

  /**
   * Whether the entity's grants, or the context's roles on every asset, give {@code permission}.
   */
  boolean hasPermission(
      Supplier<BaseEntity> entitySupplier, String assetClass, Permission permission);

  /**
   * Whether the access list's grants, or the context's roles on every asset, give {@code
   * permission}.
   */
  boolean hasPermission(Acl acl, String assetClass, Permission permission);

  boolean hasPermissionOnEveryAsset(String assetClass, Permission permission);
}
