package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;

/**
 * Generic repository interface providing basic CRUD operations
 *
 * @param <T> the entity type
 */
public interface Repository<T extends BaseEntity> extends ReadRepository<T> {

  T insert(T entity);

  /**
   * Replace an entity, regardless of the version currently stored.
   *
   * @param id the entity ID
   * @param entity the replacement entity
   * @return the replaced entity
   * @throws com.agentengine.util.common.exception.AssetNotFoundException if no entity has that ID
   */
  T update(String id, T entity);

  /**
   * Replace an entity only if the stored version still matches {@code expectedVersion}. Use this
   * when the entity was read, modified and written back, and a concurrent modification must not be
   * silently overwritten.
   *
   * @param id the entity ID
   * @param expectedVersion the version the caller read
   * @param entity the replacement entity
   * @return the replaced entity
   * @throws com.agentengine.util.common.exception.StaleStateException if the stored version differs
   */
  T update(String id, Long expectedVersion, T entity);

  /**
   * Apply a partial update to an entity.
   *
   * @param id the entity ID
   * @param update the update operations
   * @return the updated entity, or null if no entity has that ID
   */
  T update(String id, Update update);

  /**
   * Find an entity matching the query, apply the update, and return the modified entity.
   *
   * @param query the query to find the entity
   * @param update the update operations
   * @return the updated entity, or null if no entity matched
   */
  T findOneAndUpdate(Query query, Update update);

  /**
   * Update a single entity matching the filter.
   *
   * @param filter the filter to match the entity
   * @param update the update operations
   * @return the number of modified entities (0 or 1)
   */
  long updateOne(Filter filter, Update update);

  /**
   * Update every entity matching the filter.
   *
   * @param filter the filter to match entities
   * @param update the update operations
   * @return the number of modified entities
   */
  long updateMany(Filter filter, Update update);

  /**
   * Save an entity, inserting it when it has no ID and replacing it otherwise.
   *
   * @param entity the entity to save
   * @return the saved entity
   */
  T save(T entity);

  /**
   * Delete an entity by its ID
   *
   * @param id the entity ID
   * @return true if the entity was deleted, false if it didn't exist
   */
  boolean deleteById(String id);

  long deleteByQuery(Query query);
}
