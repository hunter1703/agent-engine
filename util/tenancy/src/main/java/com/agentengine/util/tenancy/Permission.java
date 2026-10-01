package com.agentengine.util.tenancy;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public enum Permission {
  UNKNOWN,
  READ,
  EDIT(READ),
  DELETE(EDIT),
  SHARE(READ),
  CREATE(true);

  private final Set<Permission> impliedPermissions;
  private final boolean onEveryAssetOnly;

  Permission() {
    this(false);
  }

  Permission(final Permission... directlyImplied) {
    this(false, directlyImplied);
  }

  Permission(boolean onEveryAssetOnly, final Permission... directlyImplied) {
    this.onEveryAssetOnly = onEveryAssetOnly;
    final Set<Permission> all = new LinkedHashSet<>();
    for (final Permission permission : directlyImplied) {
      all.addAll(permission.impliedPermissions);
    }
    all.add(this);
    this.impliedPermissions = Set.copyOf(all);
  }

  public static Permission valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return Permission.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return UNKNOWN;
    }
  }

  /** This permission and every permission it implies. */
  public Set<Permission> getImpliedPermissions() {
    return impliedPermissions;
  }

  public boolean isOnEveryAssetOnly() {
    return onEveryAssetOnly;
  }
}
