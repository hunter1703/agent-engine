package com.agentengine.util.tenancy;

import com.agentengine.util.context.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Roles to add to and remove from principals, on one asset or, when its class and id are both null,
 * on every asset. Each map goes from a principal, as written, to the ids of the roles to add to it
 * or remove from it; a principal that is not a valid one is refused.
 */
public record SharingChange(
    String assetClass,
    String assetId,
    Map<String, Set<String>> add,
    Map<String, Set<String>> remove) {

  public SharingChange {
    if ((assetClass == null) != (assetId == null)) {
      throw new IllegalArgumentException(
          "Sharing is either of one asset (its class and id) or on every asset (neither)");
    }
    add = add == null ? Map.of() : Map.copyOf(add);
    remove = remove == null ? Map.of() : Map.copyOf(remove);
    add.keySet().forEach(Principal::parse);
    remove.keySet().forEach(Principal::parse);
    for (final Map.Entry<String, Set<String>> entry : add.entrySet()) {
      if (!Collections.disjoint(entry.getValue(), remove.getOrDefault(entry.getKey(), Set.of()))) {
        throw new IllegalArgumentException(
            "Roles of " + entry.getKey() + " are both added and removed");
      }
    }
  }

  public static SharingChange ofAsset(
      final String assetClass, final String assetId, final Map<String, Set<String>> add) {
    return new SharingChange(assetClass, assetId, add, null);
  }

  public static SharingChange onEveryAsset(final Map<String, Set<String>> add) {
    return new SharingChange(null, null, add, null);
  }

  public boolean isOnEveryAsset() {
    return assetId == null;
  }

  /** The ids of every role this change adds or removes. */
  public Set<String> roleIds() {
    final Set<String> roleIds = new HashSet<>();
    add.values().forEach(roleIds::addAll);
    remove.values().forEach(roleIds::addAll);
    return roleIds;
  }

  /** Every principal this change adds roles to or removes roles from. */
  public Set<Principal> principals() {
    final Set<Principal> principals = new HashSet<>();
    add.keySet().forEach(principal -> principals.add(Principal.parse(principal)));
    remove.keySet().forEach(principal -> principals.add(Principal.parse(principal)));
    return principals;
  }
}
