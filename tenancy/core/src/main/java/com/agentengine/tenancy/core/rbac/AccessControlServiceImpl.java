package com.agentengine.tenancy.core.rbac;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.tenancy.core.helpers.AssetPermissionHelper;
import com.agentengine.tenancy.core.repository.RoleMappingRepository;
import com.agentengine.tenancy.core.repository.RoleRepository;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.tasks.Task;
import com.agentengine.util.tasks.TaskService;
import com.agentengine.util.tasks.TaskStatus;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionChecker;
import com.agentengine.util.tenancy.PermissionUtils;
import com.agentengine.util.tenancy.SharingChange;
import com.google.common.cache.CacheBuilder;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Owns role mappings, and calculates every asset's access list from them. A sharing change marks
 * the changed mappings pending and asks for their assets to be processed; processing an asset
 * recalculates its access list from all its mappings and stores it only while the asset still holds
 * the version read, so an older calculation never overwrites a newer one. Role and role mapping
 * tasks left pending for too long are submitted again on request.
 */
@Singleton
@Unremovable
public class AccessControlServiceImpl implements AccessControlService {

  static final String ROLE_CACHE_NAME = "ROLE_CACHE";

  private final RoleMappingRepository roleMappingRepository;
  private final RoleRepository roleRepository;
  private final DistributedCache<Role> roleCache;
  // Looked up lazily, since access control itself reads roles through this service.
  private final LazyLoader<PermissionChecker> permissionChecker;
  // Built lazily, since the helper of tenancy's own assets reaches repositories that use this
  // service.
  private final LazyLoader<Map<String, AssetPermissionHelper>> assetClassVsHelper;
  // Looked up lazily, since the role mapping tasks recalculate access lists through this service.
  private final LazyLoader<RoleMappingTaskService> roleMappingTaskService;
  private final LazyLoader<RoleTaskService> roleTaskService;

  @Inject
  public AccessControlServiceImpl(
      final RoleMappingRepository roleMappingRepository,
      final RoleRepository roleRepository,
      final Instance<AssetPermissionHelper> assetPermissionHelpers,
      final DistributedCacheManager distributedCacheManager,
      final Provider<PermissionChecker> permissionChecker,
      final Provider<RoleMappingTaskService> roleMappingTaskService,
      final Provider<RoleTaskService> roleTaskService) {
    this.roleMappingRepository = roleMappingRepository;
    this.roleRepository = roleRepository;
    this.roleCache =
        new DistributedCache.Builder<Role>(ROLE_CACHE_NAME, distributedCacheManager)
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS))
            .build();
    this.permissionChecker = new LazyLoader<>(permissionChecker::get);
    this.roleMappingTaskService = new LazyLoader<>(roleMappingTaskService::get);
    this.roleTaskService = new LazyLoader<>(roleTaskService::get);
    this.assetClassVsHelper =
        new LazyLoader<>(
            () ->
                assetPermissionHelpers.stream()
                    .collect(
                        Collectors.toUnmodifiableMap(
                            AssetPermissionHelper::assetClass, helper -> helper)));
  }

  @Override
  public Map<String, Map<String, Set<String>>> getAssetClassPermissions(
      final Collection<String> principals) {
    final Set<String> mappingIds = new HashSet<>();
    for (final String principal : principals) {
      mappingIds.add(RoleMapping.id(principal, null, null));
    }
    final Map<String, Map<String, Set<String>>> principalVsAssetClassVsPermissions =
        new HashMap<>();
    for (final RoleMapping mapping : roleMappingRepository.findByIds(mappingIds).values()) {
      final Map<String, Set<String>> assetClassVsPermissions = new HashMap<>();
      for (final Entry<String, Set<Permission>> entry :
          assetClassVsPermissions(mapping.getRoleIds()).entrySet()) {
        final Set<String> names = new HashSet<>();
        for (final Permission permission : entry.getValue()) {
          names.add(permission.name());
        }
        assetClassVsPermissions.put(entry.getKey(), names);
      }
      principalVsAssetClassVsPermissions.put(mapping.getPrincipal(), assetClassVsPermissions);
    }
    return principalVsAssetClassVsPermissions;
  }

  @Override
  public void deleteAcls(final String assetClass, final Collection<String> assetIds) {
    if (!Context.require().isSystem()) {
      throw new UnauthorizedException("Access lists are deleted by the system only");
    }
    roleMappingRepository.deleteForAssets(assetClass, assetIds);
  }

  @Override
  public void updateSharing(final List<SharingChange> changes) {
    final Set<String> roleIds = new HashSet<>();
    final Set<String> onEveryAssetRoleIds = new HashSet<>();
    final Map<String, List<SharingChange>> assetClassVsChanges = new LinkedHashMap<>();
    for (final SharingChange change : changes) {
      roleIds.addAll(change.roleIds());
      if (change.isOnEveryAsset()) {
        onEveryAssetRoleIds.addAll(change.roleIds());
      } else {
        assetClassVsChanges
            .computeIfAbsent(change.assetClass(), _ -> new ArrayList<>())
            .add(change);
      }
    }
    requireRolesExist(roleIds);
    if (!Context.require().isSystem()) {
      assetClassVsChanges.forEach(this::requireCanShare);
      requireCanShareOnEveryAsset(onEveryAssetRoleIds);
    }

    for (final SharingChange change : changes) {
      change
          .add()
          .forEach(
              (principal, roles) ->
                  roleMappingRepository.addRoles(
                      principal, change.assetClass(), change.assetId(), roles, TaskStatus.PENDING));
      change
          .remove()
          .forEach(
              (principal, roles) ->
                  roleMappingRepository.removeRoles(
                      principal, change.assetClass(), change.assetId(), roles));
    }
  }

  @Override
  public Map<String, Acl> shareNewAssets(final List<SharingChange> changes) {
    if (!Context.require().isSystem()) {
      throw new UnauthorizedException("New assets are shared by the system only");
    }
    final Set<String> roleIds = new HashSet<>();
    for (final SharingChange change : changes) {
      if (change.isOnEveryAsset() || !change.remove().isEmpty()) {
        throw new IllegalArgumentException("A new asset is shared by adding roles on it only");
      }
      roleIds.addAll(change.roleIds());
    }
    requireRolesExist(roleIds);
    // A new asset has no mappings but those written here, so they alone make its access list.
    final Map<String, List<RoleMapping>> assetIdVsMappings = new LinkedHashMap<>();
    for (final SharingChange change : changes) {
      change
          .add()
          .forEach(
              (principal, roles) ->
                  assetIdVsMappings
                      .computeIfAbsent(change.assetId(), _ -> new ArrayList<>())
                      .add(
                          roleMappingRepository.addRoles(
                              principal,
                              change.assetClass(),
                              change.assetId(),
                              roles,
                              TaskStatus.DONE)));
    }
    final Map<String, Acl> assetIdVsAcl = new LinkedHashMap<>();
    assetIdVsMappings.forEach(
        (assetId, mappings) ->
            assetIdVsAcl.put(
                assetId,
                new Acl(calculateGrants(mappings.getFirst().getAssetClass(), mappings), 1)));
    return assetIdVsAcl;
  }

  @Override
  public void recalculateAcl(final String assetClass, final String assetId) {
    if (!Context.require().isSystem()) {
      throw new UnauthorizedException("Access lists are recalculated by the system only");
    }
    final AssetPermissionHelper helper = assetPermissionHelper(assetClass);
    final Acl stored = helper.getAcls(List.of(assetId)).get(assetId);
    if (stored == null) {
      roleMappingRepository.deleteForAssets(assetClass, List.of(assetId));
      return;
    }
    final List<RoleMapping> mappings = roleMappingRepository.findForAsset(assetClass, assetId);
    final Acl recalculated = new Acl(calculateGrants(assetClass, mappings), stored.version() + 1);
    if (!helper.applyAcls(Map.of(assetId, recalculated)).contains(assetId)) {
      throw new StaleStateException(RoleMapping.partitionId(assetClass, assetId), stored.version());
    }
  }

  @Override
  public void resubmitStaleRoleMappings() {
    resubmitStale(roleMappingTaskService.get());
  }

  @Override
  public void resubmitStaleRoles() {
    resubmitStale(roleTaskService.get());
  }

  private AssetPermissionHelper assetPermissionHelper(final String assetClass) {
    final AssetPermissionHelper helper = assetClassVsHelper.get().get(assetClass);
    if (helper == null) {
      throw new ConfigurationException("No service keeps assets of class " + assetClass);
    }
    return helper;
  }

  private void requireRolesExist(final Set<String> roleIds) {
    final Map<String, Role> idVsRole = getRoles(roleIds);
    for (final String roleId : roleIds) {
      if (!idVsRole.containsKey(roleId)) {
        throw new IllegalArgumentException("No such role: " + roleId);
      }
    }
  }

  /**
   * Sharing an asset takes SHARE on it, and every permission the roles changed on it grant — so no
   * one hands out more than they hold. Reads the access lists of all the class's assets at once, as
   * the system, and checks them against the caller.
   */
  private void requireCanShare(final String assetClass, final List<SharingChange> changes) {
    final Map<String, Acl> assetIdVsAcl =
        Context.require()
            .asSystemCaller()
            .get(
                () ->
                    assetPermissionHelper(assetClass)
                        .getAcls(changes.stream().map(SharingChange::assetId).toList()));
    for (final SharingChange change : changes) {
      final Acl acl = assetIdVsAcl.get(change.assetId());
      final Set<Permission> required =
          new HashSet<>(permissionsForAssetClass(assetClass, change.roleIds()));
      required.add(Permission.SHARE);
      for (final Permission permission : required) {
        if (!permissionChecker.get().hasPermission(acl, assetClass, permission)) {
          throw new UnauthorizedException(assetClass, change.assetId());
        }
      }
    }
  }

  /**
   * A role on every asset can only be handed out by someone who holds, and may share, all it
   * grants.
   */
  private void requireCanShareOnEveryAsset(final Set<String> roleIds) {
    for (final Entry<String, Set<Permission>> entry : assetClassVsPermissions(roleIds).entrySet()) {
      final Set<Permission> required = new HashSet<>(entry.getValue());
      required.add(Permission.SHARE);
      for (final Permission permission : required) {
        if (!permissionChecker.get().hasPermissionOnEveryAsset(entry.getKey(), permission)) {
          throw new UnauthorizedException(entry.getKey(), null);
        }
      }
    }
  }

  private List<String> calculateGrants(final String assetClass, final List<RoleMapping> mappings) {
    final Set<String> roleIds = new HashSet<>();
    for (final RoleMapping mapping : mappings) {
      roleIds.addAll(mapping.getRoleIds());
    }
    final Map<String, Role> idVsRole = getRoles(roleIds);
    final Set<String> grants = new LinkedHashSet<>();
    for (final RoleMapping mapping : mappings) {
      grants.addAll(
          PermissionUtils.grants(
              Principal.parse(mapping.getPrincipal()),
              permissionsForAssetClass(
                  assetClass, assetClassVsPermissions(mapping.getRoleIds(), idVsRole))));
    }
    return new ArrayList<>(grants);
  }

  private Set<Permission> permissionsForAssetClass(
      final String assetClass, final Collection<String> roleIds) {
    return permissionsForAssetClass(assetClass, assetClassVsPermissions(roleIds));
  }

  private static Set<Permission> permissionsForAssetClass(
      final String assetClass, final Map<String, Set<Permission>> classVsPermissions) {
    final Set<Permission> permissions =
        new HashSet<>(classVsPermissions.getOrDefault(assetClass, Set.of()));
    permissions.removeIf(Permission::isOnEveryAssetOnly);
    return permissions;
  }

  /** The roles that exist among {@code roleIds}, reading only those not already cached. */
  private Map<String, Role> getRoles(final Collection<String> roleIds) {
    return CollectionUtils.isEmpty(roleIds)
        ? Map.of()
        : roleCache.getAll(roleIds, roleRepository::findByIds);
  }

  private Map<String, Set<Permission>> assetClassVsPermissions(final Collection<String> roleIds) {
    return assetClassVsPermissions(roleIds, getRoles(roleIds));
  }

  private static Map<String, Set<Permission>> assetClassVsPermissions(
      final Collection<String> roleIds, final Map<String, Role> idVsRole) {
    final Map<String, Set<Permission>> classVsPermissions = new HashMap<>();
    for (final String roleId : roleIds) {
      final Role role = idVsRole.get(roleId);
      if (role == null) {
        continue;
      }
      role.getAssetClassVsPermissions()
          .forEach(
              (assetClass, names) -> {
                final Set<Permission> permissions =
                    classVsPermissions.computeIfAbsent(assetClass, _ -> new HashSet<>());
                for (final String name : names) {
                  permissions.addAll(Permission.valueOfOrDefault(name).getImpliedPermissions());
                }
                permissions.remove(Permission.UNKNOWN);
              });
    }
    return classVsPermissions;
  }

  /** Submits again every task of {@code service} pending for longer than it allows. */
  private static <T extends Task> void resubmitStale(final TaskService<T> service) {
    service
        .findStale(System.currentTimeMillis() - service.staleAfter().toMillis())
        .forEach(service::submit);
  }
}
