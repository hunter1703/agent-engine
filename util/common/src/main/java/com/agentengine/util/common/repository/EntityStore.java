package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The storage of one entity class in a backend, read and written as given — no validation, no
 * access checks, and no fields filled in beyond the ids of new entities. It resolves the current
 * customer's storage on every call.
 */
public interface EntityStore<T extends BaseEntity> {

  Class<T> entityClass();

  /** A new id, in the form this store keeps ids in. */
  String newId();

  /**
   * Stores a new entity, giving it an id when it has none.
   *
   * @throws com.agentengine.util.common.exception.DuplicateAssetException when an entity already
   *     has its id
   */
  T insert(T entity);

  /**
   * Stores new entities, giving an id to each that has none.
   *
   * @throws com.agentengine.util.common.exception.DuplicateAssetException when an entity already
   *     has one of their ids
   */
  List<T> insertMany(List<T> entities);

  T findById(String id, List<String> includeFields, List<String> excludeFields);

  Map<String, T> findByIds(
      Collection<String> ids, List<String> includeFields, List<String> excludeFields);

  PaginatedResult<T> findByQuery(Query query);

  /**
   * Writes {@code entity} whole over the stored one, as it is, its version included — all but its
   * access list: the stored one is kept. With {@code expectedVersion} set, only while the stored
   * version is still that one. With {@code upsert} and no {@code expectedVersion}, an entity not
   * stored yet is stored.
   *
   * @throws com.agentengine.util.common.exception.StaleStateException when the stored version is
   *     not {@code expectedVersion}
   */
  T replace(T entity, Long expectedVersion, boolean upsert, T existing);

  /** Applies {@code update} to the first entity {@code query} matches and returns it, or null. */
  T findOneAndUpdate(Query query, Update update);

  long updateOne(Filter filter, Update update);

  /**
   * Applies {@code update} to the first entity {@code filter} matches or, when none does, to a new
   * one made of {@code filter}'s equality conditions, and returns it after the update.
   */
  T upsertOne(Filter filter, Update update);

  long updateMany(Filter filter, Update update);

  /** Deletes the entity, whatever its version. Returns whether there was one. */
  boolean deleteById(String id);

  /** Deletes the entity, only while it is still at {@code version}. Returns whether it did. */
  boolean delete(String id, long version);

  /** Deletes every entity {@code filter} matches, or every entity when it is null. Returns the number of entities deleted. */
  long deleteByFilter(Filter filter);

  /** Sets up the current customer's collection, or the shared one for a global store. */
  void setup();
}
