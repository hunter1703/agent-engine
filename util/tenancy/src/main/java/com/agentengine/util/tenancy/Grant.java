package com.agentengine.util.tenancy;

import com.agentengine.util.context.Principal;
import java.util.Objects;

public record Grant(Principal principal, Permission permission) {

  public Grant {
    Objects.requireNonNull(principal, "principal");
    Objects.requireNonNull(permission, "permission");
  }

  public static Grant parse(final String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Grant string cannot be null or blank");
    }
    final int hashIdx = value.indexOf('#');
    if (hashIdx <= 0 || hashIdx == value.length() - 1) {
      throw new IllegalArgumentException("Invalid grant format: " + value);
    }
    final Principal principal = Principal.parse(value.substring(0, hashIdx));
    final Permission permission = Permission.valueOf(value.substring(hashIdx + 1));
    return new Grant(principal, permission);
  }

  @Override
  public String toString() {
    return principal.toString() + "#" + permission.name();
  }
}
