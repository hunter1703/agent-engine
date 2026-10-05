package com.agentengine.tenancy;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionChecker;
import com.agentengine.util.tenancy.PermissionUtils;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Decides access from an entity's grants and the context principals' roles on every asset, which
 * the tenancy service calculates and this keeps cached.
 */
@Singleton
public class PermissionCheckerImpl implements PermissionChecker {
  private static final long CACHE_TTL_SECONDS = 300;
  private final PermissionService permissionService;
  private final DistributedCache<Map<String, Set<Permission>>> permissionsOnEveryAssetCache;

  public PermissionCheckerImpl(
      final PermissionService permissionService,
      final DistributedCacheManager distributedCacheManager) {
    this.permissionService = permissionService;
    this.permissionsOnEveryAssetCache =
        new DistributedCache.Builder<Map<String, Set<Permission>>>(
                AccessControlService.PERMISSIONS_ON_EVERY_ASSET_CACHE, distributedCacheManager)
            .scope(CacheScope.CUSTOMER)
            .localCache(
                CacheBuilder.newBuilder().expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS))
            .build();
  }

  @Override
  public boolean hasPermission(
      final Supplier<BaseEntity> entitySupplier,
      final String assetClass,
      final Permission permission) {
    return aclGivesPermission(
        () -> {
          final BaseEntity entity = entitySupplier.get();
          return entity == null ? null : entity.getAcl();
        },
        assetClass,
        permission);
  }

  @Override
  public boolean hasPermission(
      final Acl acl, final String assetClass, final Permission permission) {
    return aclGivesPermission(() -> acl, assetClass, permission);
  }

  @Override
  public boolean hasPermissionOnEveryAsset(final String assetClass, final Permission permission) {
    if (isSystem()) {
      return true;
    }
    return actsForUser() && contextHasPermissionOnEveryAsset(assetClass, permission);
  }

  /**
   * Whether the access list, read only when the context is a user's, or the context's roles on
   * every asset, give {@code permission}; always for the system.
   */
  private boolean aclGivesPermission(
      final Supplier<Acl> aclSupplier, final String assetClass, final Permission permission) {
    if (isSystem()) {
      return true;
    }
    if (!actsForUser()) {
      return false;
    }
    final Acl acl = aclSupplier.get();
    if (acl == null) {
      return false;
    }
    if (CollectionUtils.isNotEmpty(acl.grants())
        && !Collections.disjoint(acl.grants(), PermissionUtils.contextGrants(permission))) {
      return true;
    }
    return contextHasPermissionOnEveryAsset(assetClass, permission);
  }

  private boolean contextHasPermissionOnEveryAsset(
      final String assetClass, final Permission permission) {
    for (final Map<String, Set<Permission>> assetClassVsPermissions :
        permissionsOnEveryAsset(PermissionUtils.contextPrincipals())) {
      if (assetClassVsPermissions.getOrDefault(assetClass, Set.of()).contains(permission)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Each principal's permissions on every asset, asset class to permissions, fetching every
   * uncached one in a single call.
   */
  private Collection<Map<String, Set<Permission>>> permissionsOnEveryAsset(
      final Set<Principal> principals) {
    return permissionsOnEveryAssetCache
        .getAll(principals.stream().map(Principal::toString).toList(), this::loadPermissions)
        .values();
  }

  /** Each of the principals' permissions on every asset; none for one tenancy maps no role to. */
  private Map<String, Map<String, Set<Permission>>> loadPermissions(
      final Collection<String> principals) {
    return permissionService.getAssetClassPermissions(principals);
  }

  private boolean actsForUser() {
    return Context.currentUserId().isPresent();
  }

  private static boolean isSystem() {
    return Context.current().map(Context::isSystem).orElse(false);
  }
}
