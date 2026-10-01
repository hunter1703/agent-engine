package com.agentengine.util.tenancy;

import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.Repository;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** A repository of {@link Permissioned} entities, whose access lists tenancy keeps. */
public interface PermissionedRepository<T extends BaseEntity> extends Repository<T> {

  boolean hasPermission(String id, Permission permission);

  /** The ids among {@code ids} of entities the caller holds {@code permission} on. */
  Set<String> findPermittedIds(Collection<String> ids, Permission permission);

  /**
   * The ids of the entities {@code query} matches that the caller holds {@code permission} on, a
   * page of them as {@code query} asks.
   */
  PaginatedResult<String> findPermittedIds(Query query, Permission permission);

  /**
   * The stored access lists of the entities among {@code ids} that exist; to the system only.
   *
   * @throws com.agentengine.util.common.exception.UnauthorizedException when the caller is not the
   *     system
   */
  Map<String, Acl> readAcls(Collection<String> ids);

  /**
   * Stores each access list whose version is newer than the entity's current one; by the system
   * only, since tenancy calculates access lists from role mappings. Returns the ids of the entities
   * whose access list it stored.
   *
   * @throws com.agentengine.util.common.exception.UnauthorizedException when the caller is not the
   *     system
   */
  Set<String> applyAcls(Map<String, Acl> idVsAcl);
}
