package com.agentengine.tenancy.core.rbac;

import com.agentengine.tenancy.beans.Role;
import com.agentengine.util.tenancy.Permission;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public final class PermissionUtils {

  private PermissionUtils() {
    // Utilities class
  }

  public static Map<String, Set<Permission>> assetClassVsPermissions(
      final Map<String, Role> idVsRole) {
    final Map<String, Set<Permission>> classVsPermissions = new HashMap<>();
    for (final Entry<String, Role> entry : idVsRole.entrySet()) {
      final Role role = entry.getValue();
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
}
