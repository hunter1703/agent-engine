package com.agentengine.util.common.beans;

import com.agentengine.util.common.utils.CollectionUtils;

import java.util.List;

/**
 * An entity's access list: its grant tokens, each of the form {@code <principal>#<permission>} —
 * e.g. {@code "User/5#READ"} or {@code "User/*:Agent/A#EDIT"} — at the version tenancy calculated
 * them at. A reader checks access by whether any of its own tokens appears in {@code grants}, never
 * by resolving roles itself.
 */
public record Acl(List<String> grants, long version) {

  public Acl{
    grants = CollectionUtils.nullSafeList(grants);
  }

  public static final String FIELD_GRANTS = "grants";
  public static final String FIELD_VERSION = "version";

  /** No grants, at version 0: the access list of an entity tenancy has not calculated one for. */
  public static final Acl EMPTY = new Acl(List.of(), 0);
}
