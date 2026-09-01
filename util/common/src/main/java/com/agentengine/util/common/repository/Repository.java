package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import java.util.List;

public interface Repository<T extends BaseEntity> extends ReadRepository<T> {

  T insert(T entity);

  List<T> insertMany(List<T> entities);

  /**
   * Replace an entity, regardless of the version currently stored.
   *
   * @throws com.agentengine.util.common.exception.AssetNotFoundException if no entity has that ID
   */
  T update(String id, T entity);

  /**
   * Replace an entity only if the stored version still matches {@code expectedVersion}. Use this
   * when the entity was read, modified and written back, and a concurrent modification must not be
   * silently overwritten.
   *
   * @param expectedVersion the version the caller read
   * @throws com.agentengine.util.common.exception.StaleStateException if the stored version differs
   */
  T update(String id, Long expectedVersion, T entity);

  /**
   * Apply a partial update to an entity.
   *
   * @return the updated entity, or null if no entity has that ID
   */
  T update(String id, Update update);

  /**
   * Find an entity matching the query, apply the update, and return the modified entity.
   *
   * @return the updated entity, or null if no entity matched
   */
  T findOneAndUpdate(Query query, Update update);

  /**
   * Update a single entity matching the filter.
   *
   * @return the number of modified entities (0 or 1)
   */
  long updateOne(Filter filter, Update update);

  /** Update every entity matching the filter. */
  long updateMany(Filter filter, Update update);

  /** Save an entity, inserting it when it has no ID and replacing it otherwise. */
  T save(T entity);

  /**
   * @return true if the entity was deleted, false if it didn't exist
   */
  boolean deleteById(String id);

  long deleteByQuery(Query query);
}
