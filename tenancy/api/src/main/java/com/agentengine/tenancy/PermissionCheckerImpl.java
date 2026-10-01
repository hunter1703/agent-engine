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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Decides access from an entity's grants and the context principals' roles on every asset, which
 * the tenancy service calculates and this keeps cached.
 */
@Singleton
public class PermissionCheckerImpl implements PermissionChecker {
  private static final long CACHE_TTL_SECONDS = 300;
  private static final String ACTIVE_USERS_CACHE = "ACTIVE_USERS";
  private static final long ACTIVE_USERS_TTL_SECONDS = 60;

  private final AccessControlService accessControlService;
  // Looked up lazily, since in the tenancy service the users' own repository checks access here.
  private final DistributedCache<Map<String, Set<Permission>>> permissionsOnEveryAssetCache;
  private final DistributedCache<Boolean> activeUsersCache;

  private final LazyLoader<UserService> userService;

  public PermissionCheckerImpl(
      final AccessControlService accessControlService,
      final Provider<UserService> userService,
      final DistributedCacheManager distributedCacheManager) {
    this.accessControlService = accessControlService;
    this.userService = new LazyLoader<>(userService::get);
    this.permissionsOnEveryAssetCache =
        new DistributedCache.Builder<Map<String, Set<Permission>>>(
                AccessControlService.PERMISSIONS_ON_EVERY_ASSET_CACHE, distributedCacheManager)
            .scope(CacheScope.CUSTOMER)
            .localCache(
                CacheBuilder.newBuilder().expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS))
            .build();
    this.activeUsersCache =
        new DistributedCache.Builder<Boolean>(ACTIVE_USERS_CACHE, distributedCacheManager)
            .scope(CacheScope.CUSTOMER)
            .localCache(
                CacheBuilder.newBuilder()
                    .expireAfterWrite(ACTIVE_USERS_TTL_SECONDS, TimeUnit.SECONDS))
            .loader(
                userId ->
                    Context.require()
                        .asSystemCaller()
                        .get(() -> this.userService.get().isActive(userId)))
            .build();
  }

  @Override
  public boolean hasPermission(
      final Supplier<BaseEntity> entitySupplier,
      final String assetClass,
      final Permission permission) {
    if (isSystem()) {
      return true;
    }
    if (!actsForActiveUser()) {
      return false;
    }
    final BaseEntity entity = entitySupplier.get();
    return entity != null && grantsGivePermission(entity.getAcl().grants(), assetClass, permission);
  }

  @Override
  public boolean hasPermission(
      final Acl acl, final String assetClass, final Permission permission) {
    if (isSystem()) {
      return true;
    }
    if (!actsForActiveUser()) {
      return false;
    }
    return acl != null && grantsGivePermission(acl.grants(), assetClass, permission);
  }

  @Override
  public boolean hasPermissionOnEveryAsset(final String assetClass, final Permission permission) {
    if (isSystem()) {
      return true;
    }
    if (!actsForActiveUser()) {
      return false;
    }
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
      final List<Principal> principals) {
    return permissionsOnEveryAssetCache
        .getAll(principals.stream().map(Principal::toString).toList(), this::loadPermissions)
        .values();
  }

  /** Each of the principals' permissions on every asset; none for one tenancy maps no role to. */
  private Map<String, Map<String, Set<Permission>>> loadPermissions(
      final Collection<String> principals) {
    final Map<String, Map<String, Set<String>>> loaded =
        accessControlService.getAssetClassPermissions(principals);
    final Map<String, Map<String, Set<Permission>>> principalVsPermissions = new HashMap<>();
    for (final String principal : principals) {
      principalVsPermissions.put(
          principal, parsePermissions(loaded.getOrDefault(principal, Map.of())));
    }
    return principalVsPermissions;
  }

  /** Asset class to the permissions named, leaving out names this version doesn't know. */
  private static Map<String, Set<Permission>> parsePermissions(
      final Map<String, Set<String>> assetClassVsNames) {
    final Map<String, Set<Permission>> assetClassVsPermissions = new HashMap<>();
    assetClassVsNames.forEach(
        (assetClass, names) -> {
          final Set<Permission> permissions = new HashSet<>();
          for (final String name : names) {
            final Permission permission = Permission.valueOfOrDefault(name);
            if (permission != Permission.UNKNOWN) {
              permissions.add(permission);
            }
          }
          assetClassVsPermissions.put(assetClass, Collections.unmodifiableSet(permissions));
        });
    return assetClassVsPermissions;
  }

  /**
   * Whether the context acts for a user who may act at all. A disabled user's stored contexts —
   * scheduled jobs, running sessions — lose every permission within {@link
   * #ACTIVE_USERS_TTL_SECONDS}.
   */
  /** Whether {@code grants}, or the context's roles on every asset, give {@code permission}. */
  private boolean grantsGivePermission(
      final List<String> grants, final String assetClass, final Permission permission) {
    if (CollectionUtils.isNotEmpty(grants)
        && !Collections.disjoint(grants, PermissionUtils.contextGrants(permission))) {
      return true;
    }
    return hasPermissionOnEveryAsset(assetClass, permission);
  }

  private boolean actsForActiveUser() {
    final String userId = Context.currentUserId().orElse(null);
    return userId != null && activeUsersCache.get(userId);
  }

  private static boolean isSystem() {
    return Context.current().map(Context::isSystem).orElse(false);
  }
}
