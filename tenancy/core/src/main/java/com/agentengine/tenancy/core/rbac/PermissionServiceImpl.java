package com.agentengine.tenancy.core.rbac;

import com.agentengine.tenancy.PermissionService;
import com.agentengine.tenancy.RoleService;
import com.agentengine.tenancy.core.repository.RoleMappingRepository;
import com.agentengine.util.tenancy.Permission;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
@Unremovable
public class PermissionServiceImpl implements PermissionService {

  private final RoleMappingRepository roleMappingRepository;
  private final RoleService roleService;

  @Inject
  public PermissionServiceImpl(
      final RoleMappingRepository roleMappingRepository, final RoleService roleService) {
    this.roleMappingRepository = roleMappingRepository;
    this.roleService = roleService;
  }

  @Override
  public Map<String, Map<String, Set<Permission>>> getAssetClassPermissions(
      final Collection<String> principals) {
    final Set<String> mappingIds = new HashSet<>();
    for (final String principal : principals) {
      mappingIds.add(RoleMapping.id(principal, null, null));
    }
    final Map<String, Map<String, Set<Permission>>> principalVsAssetClassVsPermissions =
        new HashMap<>();
    for (final RoleMapping mapping : roleMappingRepository.findByIds(mappingIds).values()) {
      principalVsAssetClassVsPermissions.put(
          mapping.getPrincipal(),
          PermissionUtils.assetClassVsPermissions(roleService.getRoles(mapping.getRoleIds())));
    }
    return principalVsAssetClassVsPermissions;
  }
}
