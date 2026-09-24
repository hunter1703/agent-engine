package com.agentengine.util.common;

import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the flattened access-control tokens stored in {@link
 * com.agentengine.util.common.beans.BaseEntity#getGrants()}. A token is a compound principal path —
 * one or more {@code type/id} segments, deepest-first-matching an exact principal, not a single
 * dimension OR'd against others — followed by the permission it grants, e.g. {@code
 * "agent/a1:READ"} or {@code "agent/a1/user/u2:READ"}. A reader builds the same kind of token for
 * whatever principal(s) it currently is, and checks for an exact match — it never parses or
 * decomposes a token.
 */
public final class GrantUtils {

  private GrantUtils() {}

  public static String build(final Permission permission, final String... pathSegments) {
    return String.join("/", pathSegments) + ":" + permission.name();
  }

  public static List<String> getGrants(final BaseEntity baseEntity) {
    if (baseEntity == null) {
      return List.of();
    }
    final long ownerUserId = baseEntity.getOwnerUserId();
    final String ownerGrant = build(Permission.READ, AssetClass.USER, String.valueOf(ownerUserId));
    final Set<String> grants = CollectionUtils.nullSafeMutableSet(baseEntity.getGrants());
    grants.add(ownerGrant);
    return new ArrayList<>(grants);
  }
}
