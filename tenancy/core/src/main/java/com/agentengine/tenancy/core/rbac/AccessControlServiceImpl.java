package com.agentengine.tenancy.core.rbac;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.tenancy.core.helpers.AssetPermissionHelper;
import com.agentengine.tenancy.core.repository.RoleMappingRepository;
import com.agentengine.tenancy.RoleService;
import com.agentengine.tenancy.beans.Role;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.agentengine.util.tasks.TaskStatus;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionChecker;
import com.agentengine.util.tenancy.SharingChange;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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

  private final RoleMappingRepository roleMappingRepository;
  private final RoleService roleService;
  private final AclCalculator aclCalculator;
  private final RoleMappingTaskService roleMappingTaskService;
  private final PermissionChecker permissionChecker;
  private final Map<String, AssetPermissionHelper> assetClassVsHelper;

  @Inject
  public AccessControlServiceImpl(
      final RoleMappingRepository roleMappingRepository,
      final RoleService roleService,
      final AclCalculator aclCalculator,
      final RoleMappingTaskService roleMappingTaskService,
      final Instance<AssetPermissionHelper> assetPermissionHelpers,
      final PermissionChecker permissionChecker) {
    this.roleMappingRepository = roleMappingRepository;
    this.roleService = roleService;
    this.aclCalculator = aclCalculator;
    this.roleMappingTaskService = roleMappingTaskService;
    this.permissionChecker = permissionChecker;
    this.assetClassVsHelper =
        assetPermissionHelpers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    AssetPermissionHelper::assetClass, helper -> helper));
  }

  @Override
  public void resubmitStaleRoleMappings() {
    roleMappingTaskService
        .findStale(System.currentTimeMillis() - roleMappingTaskService.staleAfter().toMillis())
        .forEach(roleMappingTaskService::submit);
  }


  @Override
  public void deleteAcls(final String assetClass, final Collection<String> assetIds) {
    if (!Context.require().isSystem()) {
      throw new UnauthorizedException("Access lists are deleted by the system only");
    }
    roleMappingRepository.deleteForAssets(assetClass, assetIds);
  }

  @Override
  public void forgetPrincipal(final String principal) {
    if (!Context.require().isSystem()) {
      throw new UnauthorizedException("Principals are forgotten by the system only");
    }
    roleMappingRepository.removeAllMappings(Principal.parse(principal).toString());
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
                new Acl(aclCalculator.calculateGrants(mappings.getFirst().getAssetClass(), mappings), 1)));
    return assetIdVsAcl;
  }

  private AssetPermissionHelper assetPermissionHelper(final String assetClass) {
    final AssetPermissionHelper helper = assetClassVsHelper.get(assetClass);
    if (helper == null) {
      throw new ConfigurationException("No service keeps assets of class " + assetClass);
    }
    return helper;
  }

  private void requireRolesExist(final Set<String> roleIds) {
    final Map<String, Role> idVsRole = roleService.getRoles(roleIds);
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
        if (!permissionChecker.hasPermission(acl, assetClass, permission)) {
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
        if (!permissionChecker.hasPermissionOnEveryAsset(entry.getKey(), permission)) {
          throw new UnauthorizedException(entry.getKey(), null);
        }
      }
    }
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

  private Map<String, Set<Permission>> assetClassVsPermissions(final Collection<String> roleIds) {
    return com.agentengine.tenancy.core.rbac.PermissionUtils.assetClassVsPermissions(roleService.getRoles(roleIds));
  }
}
