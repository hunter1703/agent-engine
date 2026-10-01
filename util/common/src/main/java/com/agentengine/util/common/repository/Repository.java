package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface Repository<T extends BaseEntity> {

  T findById(String id);

  T findById(String id, List<String> includeFields, List<String> excludeFields);

  Map<String, T> findByIds(Collection<String> ids);

  Map<String, T> findByIds(
      Collection<String> ids, List<String> includeFields, List<String> excludeFields);

  PaginatedResult<T> findByQuery(Query query);

  T insert(T entity);

  List<T> insertMany(List<T> entities);

  /**
   * Replaces the entity with id {@code id} by {@code entity}, only while the stored one is still at
   * {@code entity}'s version — the one its caller read.
   *
   * @throws com.agentengine.util.common.exception.AssetNotFoundException if no entity has that ID
   * @throws com.agentengine.util.common.exception.StaleStateException if the stored version differs
   */
  T update(String id, T entity);

  /**
   * Applies a partial update to the entity, only while the stored one is still at {@code entity}'s
   * version — the one its caller read.
   *
   * @return the updated entity
   * @throws com.agentengine.util.common.exception.StaleStateException if the stored entity is gone
   *     or its version differs
   */
  T update(T entity, Update update);

  /**
   * Apply a partial update to an entity, whatever its version.
   *
   * @return the updated entity, or null if no entity has that ID
   */
  T updateIgnoringVersion(String id, Update update);

  /**
   * Find an entity matching the query, apply the update whatever its version, and return the
   * modified entity.
   *
   * @return the updated entity, or null if no entity matched
   */
  T findOneAndUpdateIgnoringVersion(Query query, Update update);

  /**
   * Update a single entity matching the filter, whatever its version.
   *
   * @return the number of modified entities (0 or 1)
   */
  long updateOneIgnoringVersion(Filter filter, Update update);

  /** Update every entity matching the filter, whatever their versions. */
  long updateManyIgnoringVersion(Filter filter, Update update);

  /**
   * Inserts an entity with no id, and otherwise replaces the stored one, or stores it when there is
   * none. A replace goes through only while the stored entity is still at {@code entity}'s version
   * — the one its caller read.
   *
   * @throws com.agentengine.util.common.exception.StaleStateException if the stored version differs
   */
  T save(T entity);

  /**
   * {@link #save}, whatever version {@code entity} holds: it sets the entity to a state rather than
   * changing one its caller read. The replace is still guarded against a write landing between its
   * own read of the stored entity and its write.
   */
  T saveIgnoringVersion(T entity);

  /**
   * Deletes the entity, only while the stored one is still at {@code entity}'s version — the one
   * its caller read.
   *
   * @return true if the entity was deleted, false if it didn't exist or was written meanwhile
   */
  boolean delete(T entity);

  /**
   * Deletes the entity with id {@code id}, whatever its version.
   *
   * @return true if the entity was deleted, false if it didn't exist
   */
  boolean deleteByIdIgnoringVersion(String id);

  /**
   * Deletes every entity matching the filter, or every entity when the filter is null, whatever
   * their versions.
   */
  void deleteByFilterIgnoringVersion(Filter filter);
}
