package com.agentengine.engine.repository;

import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.engine.utils.Query;

import java.util.List;
import java.util.Optional;

/**
 * Generic repository interface providing basic CRUD operations
 *
 * @param <T>
 *          the entity type
 */
public interface Repository<T> {

  /**
   * Find an entity by its ID
   *
   * @param id
   *          the entity ID
   * @return the entity wrapped in an Optional, or empty if not found
   */
  Optional<T> findById(String id);

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