package com.agentengine.util.tenancy;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.agentengine.util.context.UserCaller;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PermissionUtils {

  /** Between a grant's principal and its permission; never part of a principal. */
  public static final String GRANT_SEPARATOR = "#";

  private static final int MAX_CACHED_CALLERS = 10_000;

  // Each caller's context principals and grants, worked out once rather than on every check: a
  // caller's principals never change.
  private static final LoadingCache<UserCaller, Set<Principal>> callerVsContextPrincipals =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_CACHED_CALLERS)
          .build(CacheLoader.from(PermissionUtils::coveringPrincipalsOf));

  private static final LoadingCache<CallerPermission, Set<String>> callerPermissionVsGrants =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_CACHED_CALLERS)
          .build(CacheLoader.from(PermissionUtils::grantsOf));

  private PermissionUtils() {}

  /** The grant giving {@code principal} {@code permission}, e.g. {@code User/*:Agent/A#READ}. */
  public static String grant(final Principal principal, final Permission permission) {
    return principal + GRANT_SEPARATOR + permission.name();
  }

  /** The grants for {@code permissions} and every permission they imply. */
  public static List<String> grants(final Principal principal, final Set<Permission> permissions) {
    final Set<String> grants = new LinkedHashSet<>();
    for (final Permission permission : permissions) {
      for (final Permission implied : permission.getImpliedPermissions()) {
        grants.add(grant(principal, implied));
      }
    }
    return new ArrayList<>(grants);
  }

  /**
   * Every principal a grant can name to reach the current context: the covering principals of each
   * principal its caller acts in. Empty for a caller that is not a user.
   */
  public static Set<Principal> contextPrincipals() {
    return Context.current()
        .flatMap(Context::userCaller)
        .map(callerVsContextPrincipals::getUnchecked)
        .orElse(Set.of());
  }

  /** The grants giving {@code permission} to any of the context principals. */
  public static Set<String> contextGrants(final Permission permission) {
    return Context.current()
        .flatMap(Context::userCaller)
        .map(
            caller ->
                callerPermissionVsGrants.getUnchecked(new CallerPermission(caller, permission)))
        .orElse(Set.of());
  }

  /** Matches entities granting {@code permission} to any of the context principals. */
  public static Filter contextGrantsFilter(final Permission permission) {
    return Filters.in(BaseEntity.FIELD_ACL_GRANTS, List.copyOf(contextGrants(permission)));
  }

  private static Set<Principal> coveringPrincipalsOf(final UserCaller caller) {
    final Set<Principal> principals = new LinkedHashSet<>();
    for (final Principal principal : caller.principals()) {
      principals.addAll(principal.coveringPrincipals());
    }
    return Set.copyOf(principals);
  }

  private static Set<String> grantsOf(final CallerPermission callerPermission) {
    final Set<String> grants = new LinkedHashSet<>();
    for (final Principal principal :
        callerVsContextPrincipals.getUnchecked(callerPermission.caller())) {
      grants.add(grant(principal, callerPermission.permission()));
    }
    return Set.copyOf(grants);
  }

  private record CallerPermission(UserCaller caller, Permission permission) {}
}
