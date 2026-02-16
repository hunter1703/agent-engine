package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.update.Update;
import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.engine.utils.Query;

import java.util.Optional;

/**
 * Generic repository interface providing basic CRUD operations
 *
 * @param <T>
 *          the entity type
 */
public interface Repository<T extends BaseEntity> {

  /**
   * Find an entity by its ID
   *
   * @param id
   *          the entity ID
   * @return the entity wrapped in an Optional, or empty if not found
   */
  Optional<T> findById(String id);

  T insert(T entity);

  T update(String id, T entity);

  /**
   * Apply a partial update to an entity.
   *
   * @param id
   *          the entity ID
   * @param update
   *          the update operations
   * @return the updated entity
   */
  T update(String id, Update update);

  /**
   * Save an entity. If the entity already exists, it will be updated.
   *
   * @param entity
   *          the entity to save
   * @return the saved entity
   */
  T save(T entity);

  /**
   * Delete an entity by its ID
   *
   * @param id
   *          the entity ID
   * @return true if the entity was deleted, false if it didn't exist
   */
  boolean deleteById(String id);

  /**
   * Find all entities
   *
   * @return a list of all entities
   */
  PaginatedResult<T> findByQuery(final Query query);

  /**
   * Get the count of all entities
   *
   * @return the number of entities
   */
  long count();
}
