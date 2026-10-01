package com.agentengine.util.tenancy;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PermissionUtils {

  /** Between a grant's principal and its permission; never part of a principal. */
  public static final String GRANT_SEPARATOR = "#";

  private PermissionUtils() {}

  /**
   * The grant token giving {@code principal} {@code permission}, e.g. {@code User/*:Agent/A#READ}.
   */
  public static String grant(final Principal principal, final Permission permission) {
    return principal + GRANT_SEPARATOR + permission.name();
  }

  /** The grant tokens for {@code permissions} and every permission they imply. */
  public static List<String> grants(final Principal principal, final Set<Permission> permissions) {
    final Set<String> keys = new LinkedHashSet<>();
    for (final Permission permission : permissions) {
      for (final Permission implied : permission.getImpliedPermissions()) {
        keys.add(grant(principal, implied));
      }
    }
    return new ArrayList<>(keys);
  }

  /**
   * Every principal a grant can name to reach the current context: the covering principals of each
   * principal its caller acts in. Empty for a caller that is not a user.
   */
  public static List<Principal> contextPrincipals() {
    final Set<Principal> principals = new LinkedHashSet<>();
    Context.current()
        .flatMap(Context::userCaller)
        .ifPresent(
            caller -> {
              for (final Principal principal : caller.principals()) {
                principals.addAll(principal.coveringPrincipals());
              }
            });
    return new ArrayList<>(principals);
  }

  public static List<String> contextGrants(final Permission permission) {
    final List<String> keys = new ArrayList<>();
    for (final Principal principal : contextPrincipals()) {
      keys.add(grant(principal, permission));
    }
    return keys;
  }

  /** Matches entities granting {@code permission} to any of the context principals. */
  public static Filter contextGrantsFilter(final Permission permission) {
    return Filters.in(BaseEntity.FIELD_ACL_GRANTS, contextGrants(permission));
  }
}
