package com.agentengine.tenancy.core.rbac;

import com.agentengine.tenancy.core.helpers.AssetPermissionHelper;
import com.agentengine.tenancy.core.repository.RoleMappingRepository;
import com.agentengine.tenancy.RoleService;
import com.agentengine.tenancy.beans.Role;
import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class AclCalculator {

  private final RoleMappingRepository roleMappingRepository;
  private final RoleService roleService;
  private final Map<String, AssetPermissionHelper> assetClassVsHelper;

  @Inject
  AclCalculator(
      final RoleMappingRepository roleMappingRepository,
      final RoleService roleService,
      final Instance<AssetPermissionHelper> assetPermissionHelpers) {
    this.roleMappingRepository = roleMappingRepository;
    this.roleService = roleService;
    this.assetClassVsHelper =
        assetPermissionHelpers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    AssetPermissionHelper::assetClass, helper -> helper));
  }

  public List<String> calculateGrants(final String assetClass, final List<RoleMapping> mappings) {
    final Set<String> roleIds = new HashSet<>();
    for (final RoleMapping mapping : mappings) {
      roleIds.addAll(mapping.getRoleIds());
    }
    final Map<String, Role> idVsRole = roleService.getRoles(roleIds);
    final Set<String> grants = new LinkedHashSet<>();
    for (final RoleMapping mapping : mappings) {
      grants.addAll(
              PermissionUtils.grants(
                      Principal.parse(mapping.getPrincipal()),
                      permissionsForAssetClass(
                              assetClass, com.agentengine.tenancy.core.rbac.PermissionUtils.assetClassVsPermissions(idVsRole))));
    }
    return new ArrayList<>(grants);
  }

  public void calculateAndUpdateGrants(final String assetClass, final String assetId) {
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

  private AssetPermissionHelper assetPermissionHelper(final String assetClass) {
    final AssetPermissionHelper helper = assetClassVsHelper.get(assetClass);
    if (helper == null) {
      throw new ConfigurationException("No service keeps assets of class " + assetClass);
    }
    return helper;
  }


  private static Set<Permission> permissionsForAssetClass(
      final String assetClass, final Map<String, Set<Permission>> classVsPermissions) {
    final Set<Permission> permissions =
        new HashSet<>(classVsPermissions.getOrDefault(assetClass, Set.of()));
    permissions.removeIf(Permission::isOnEveryAssetOnly);
    return permissions;
  }
}
