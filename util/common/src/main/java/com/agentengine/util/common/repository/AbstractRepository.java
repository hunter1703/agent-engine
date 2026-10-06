package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.query.*;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.EntityUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.validation.ValidationService;
import jakarta.inject.Inject;
import java.util.*;
import java.util.function.ToLongFunction;

/**
 * What every repository does whatever stores its entities: validating them, keeping the fields the
 * store maintains (creation and update times, version, creator), saving as insert-or-replace, and
 * telling the {@link EntityChangeListener}s of the entity class about every committed write. The
 * storing itself is the {@link EntityStore}'s.
 */
public abstract class AbstractRepository<T extends BaseEntity> implements Repository<T> {

  protected final Class<T> entityClass;
  protected final EntityStore<T> store;
  private final ValidationService validationService;
  private EntityChangeListeners changeListeners;

  protected AbstractRepository(
      final EntityStore<T> store, final ValidationService validationService) {
    this.entityClass = store.entityClass();
    this.store = store;
    this.validationService = validationService;
  }

  @Override
  public final T findById(final String id) {
    return findById(id, null, null);
  }

  @Override
  public T findById(
      final String id, final List<String> includeFields, final List<String> excludeFields) {
    return store.findById(id, includeFields, excludeFields);
  }

  @Override
  public final Map<String, T> findByIds(final Collection<String> ids) {
    return findByIds(ids, null, null);
  }

  @Override
  public Map<String, T> findByIds(
      final Collection<String> ids,
      final List<String> includeFields,
      final List<String> excludeFields) {
    return store.findByIds(ids, includeFields, excludeFields);
  }

  @Override
  public PaginatedResult<T> findByQuery(final Query query) {
    return store.findByQuery(query);
  }

  @Override
  public T insert(final T entity) {
    validateEntity(entity);
    return create(List.of(entity)).getFirst();
  }

  @Override
  public final List<T> insertMany(final List<T> entities) {
    if (CollectionUtils.isEmpty(entities)) {
      return List.of();
    }
    entities.forEach(this::validateEntity);
    return create(entities);
  }

  @Override
  public T update(final String id, final T entity) {
    return replace(id, entity == null ? null : entity.getVersion(), entity, false);
  }

  @Override
  public final T save(final T entity) {
    if (entity != null && entity.getVersion() == null) {
      return insert(entity);
    }
    return replace(
        entity == null ? null : entity.getId(),
        entity == null ? null : entity.getVersion(),
        entity,
        false);
  }

  @Override
  public final T saveIgnoringVersion(final T entity) {
    return entity != null && StringUtils.isBlank(entity.getId())
        ? insert(entity)
        : replace(entity == null ? null : entity.getId(), null, entity, true);
  }

  @Override
  public T update(final T entity, final Update update) {
    final T updated = updateFirst(new Query().withFilter(withIdAndVersionFilter(entity)), update);
    if (updated == null) {
      throw new StaleStateException(entity.getId(), entity.getVersion());
    }
    return updated;
  }

  @Override
  public T updateIgnoringVersion(final String id, final Update update) {
    return updateFirst(new Query().withFilter(Filters.eq(BaseEntity.FIELD_ID, id)), update);
  }

  @Override
  public T findOneAndUpdateIgnoringVersion(final Query query, final Update update) {
    return updateFirst(query, update);
  }

  @Override
  public long updateOneIgnoringVersion(final Filter filter, final Update update) {
    return updateMatching(filter, update, true);
  }

  @Override
  public long updateManyIgnoringVersion(final Filter filter, final Update update) {
    return updateMatching(filter, update, false);
  }

  public long updateManyIgnoringVersion(
      final Filter filter, final Update update, boolean invokeListeners) {
    return updateMatching(filter, update, false, invokeListeners);
  }

  @Override
  public boolean delete(final T entity) {
    return deleteFromStore(entity);
  }

  @Override
  public boolean deleteByIdIgnoringVersion(final String id) {
    return deleteFromStore(id);
  }

  @Override
  public long deleteByFilterIgnoringVersion(final Filter filter) {
    if (changeListeners == null || !changeListeners.hasListeners(entityClass)) {
      return store.deleteByFilter(filter);
    }

    final long[] totalDeleted = {0};
    applyInBatches(
        filter,
        batchIds -> {
          final Filter deleteFilter = Filters.in(BaseEntity.FIELD_ID, batchIds);
          long deleted = store.deleteByFilter(deleteFilter);
          if (deleted > 0) {
            totalDeleted[0] += deleted;
            publish(
                new EntityChange.Ids<>(
                    EntityChange.Type.DELETED, new java.util.HashSet<>(batchIds)));
          }
          return batchIds.size();
        },
        false);
    return totalDeleted[0];
  }

  /** The listeners every write through this repository is published to. */
  @Inject
  public final void setChangeListeners(final EntityChangeListeners changeListeners) {
    this.changeListeners = changeListeners;
  }

  /** The fields the store maintains, which an update may not set. */
  protected Set<String> contextualFields() {
    return BaseEntity.CONTEXTUAL_FIELDS;
  }

  /** Stores new, validated entities, and publishes their creation. */
  protected List<T> create(final List<T> entities) {
    return storeNew(prepareNewEntities(entities));
  }

  /**
   * Readies new entities to be stored: sets the fields the store maintains, and gives each without
   * an id a new one.
   */
  protected final List<T> prepareNewEntities(final List<T> entities) {
    for (final T entity : entities) {
      EntityUtils.prepareNew(entity);
      if (StringUtils.isBlank(entity.getId())) {
        entity.setId(store.newId());
      }
    }
    return entities;
  }

  /** Stores entities readied by {@link #prepareNewEntities}, and publishes their creation. */
  protected List<T> storeNew(final List<T> entities) {
    List<T> stored = storeNewNoListener(entities);
    publish(
        new EntityChange.Entities<>(
            EntityChange.Type.CREATED, CollectionUtils.transformToMap(stored, BaseEntity::getId)));
    return stored;
  }

  protected final List<T> storeNewNoListener(final List<T> entities) {
    final List<T> stored =
            entities.size() == 1
                    ? List.of(store.insert(entities.getFirst()))
                    : store.insertMany(entities);
    publish(
            new EntityChange.Entities<>(
                    EntityChange.Type.CREATED, CollectionUtils.transformToMap(stored, BaseEntity::getId)));
    return stored;
  }

  /**
   * Applies {@code update} to the first entity {@code query} matches and returns it, or null when
   * none does. An update that changes nothing the entity's author owns returns the first match
   * unchanged.
   */
  protected T updateFirst(final Query query, final Update update) {
    T updated = updateFirstNoListener(query, update);
    if (updated != null) {
      publish(
          new EntityChange.Entities<>(EntityChange.Type.UPDATED, Map.of(updated.getId(), updated)));
    }
    return updated;
  }

  protected final T updateFirstNoListener(final Query query, final Update update) {
    final Update applicable = EntityUtils.prepareUpdate(update, contextualFields());
    if (applicable.operations().isEmpty()) {
      return store.findByQuery(new Query(query).withPage(new Page(0, 1))).getItems().stream()
              .findFirst()
              .orElse(null);
    }
    final T updated = store.findOneAndUpdate(query, applicable);
    if (updated != null) {
      publish(
              new EntityChange.Entities<>(EntityChange.Type.UPDATED, Map.of(updated.getId(), updated)));
    }
    return updated;
  }

  /** Applies {@code update} to one or every entity {@code filter} matches. */
  protected final long updateMatching(final Filter filter, final Update update, final boolean one) {
    return updateMatching(filter, update, one, true);
  }

  protected final long updateMatching(
      final Filter filter, final Update update, final boolean one, boolean invokeListeners) {
    final Update applicable = EntityUtils.prepareUpdate(update, contextualFields());
    if (applicable.operations().isEmpty()) {
      return 0;
    }
    if (!invokeListeners || changeListeners == null || !changeListeners.hasListeners(entityClass)) {
      return one ? store.updateOne(filter, applicable) : store.updateMany(filter, applicable);
    }

    return applyInBatches(
        filter,
        batchIds -> {
          final Filter updateFilter = Filters.in(BaseEntity.FIELD_ID, batchIds);
          final long updatedCount =
              one
                  ? store.updateOne(updateFilter, applicable)
                  : store.updateMany(updateFilter, applicable);

          if (updatedCount > 0) {
            publish(new EntityChange.Ids<>(EntityChange.Type.UPDATED, new HashSet<>(batchIds)));
          }
          return updatedCount;
        },
        one);
  }

  /**
   * Applies {@code update} to the first entity {@code filter} matches or, when none does, to a new
   * one made of {@code filter}'s equality conditions, and returns it after the update. A new entity
   * is created without going through {@link #create}.
   */
  protected final T upsertOne(final Filter filter, final Update update) {
    final T upserted = upsertOneNoListener(filter, update);
    publish(
        new EntityChange.Entities<>(EntityChange.Type.UPDATED, Map.of(upserted.getId(), upserted)));
    return upserted;
  }

  protected final T upsertOneNoListener(Filter filter, Update update) {
    final List<Operation> operations =
        new ArrayList<>(EntityUtils.prepareUpdate(update, contextualFields()).operations());
    operations.add(
        Operation.setOnInsert(BaseEntity.FIELD_CREATED_TIME, System.currentTimeMillis()));
      return store.upsertOne(filter, new Update(operations));
  }

  /**
   * Writes {@code entity} over the stored one with id {@code id}; with {@code upsert}, stores it
   * when there is none.
   *
   * @throws com.agentengine.util.common.exception.StaleStateException when the stored entity is not
   *     at {@code expectedVersion} or, without one, was written since it was read here
   */
  protected T replace(
      final String id, final Long expectedVersion, final T entity, final boolean upsert) {
    validateEntity(entity);
    return write(id, expectedVersion, entity, upsert, readStored(id));
  }

  /** The stored entity with id {@code id}, to carry over its fields during a replace. */
  protected final T readStored(final String id) {
    return id == null ? null : store.findById(id, null, null);
  }

  /**
   * Writes {@code entity} whole over {@code existing}, the stored entity read for it, or as new
   * when it is null, and publishes the write. It goes through only while the stored entity is at
   * {@code expectedVersion} or, without one, at {@code existing}'s.
   */
  protected T write(
      final String id,
      final Long expectedVersion,
      final T entity,
      final boolean upsert,
      final T existing) {
    final T written = writeNoListener(id, expectedVersion, entity, upsert, existing);
    publish(
        new EntityChange.Entities<>(
            existing == null ? EntityChange.Type.CREATED : EntityChange.Type.UPDATED,
            Map.of(id, written)));
    return written;
  }

  protected final T writeNoListener(
          final String id,
          final Long expectedVersion,
          final T entity,
          final boolean upsert,
          final T existing) {
    entity.setId(id);
    EntityUtils.prepareReplacement(entity, existing);
    final Long versionToExpect =
            existing == null
                    ? null
                    : Objects.requireNonNullElse(expectedVersion, existing.getVersion());
    return store.replace(entity, versionToExpect, upsert, existing);
  }

  /** Deletes the entity with id {@code id}, whatever its version, and publishes the delete. */
  protected boolean deleteFromStore(final String id) {
    final boolean deleted = store.deleteById(id);
    if (deleted) {
      publish(new EntityChange.Ids<>(EntityChange.Type.DELETED, Set.of(id)));
    }
    return deleted;
  }

  /** Deletes the entity while it is still at its version, and publishes the delete. */
  protected boolean deleteFromStore(final T entity) {
    final boolean deleted = store.delete(entity.getId(), entity.getVersion());
    if (deleted) {
      publish(new EntityChange.Ids<>(EntityChange.Type.DELETED, Set.of(entity.getId())));
    }
    return deleted;
  }

  protected final void publish(final EntityChange<T> change) {
    if (changeListeners != null) {
      changeListeners.publish(entityClass, change);
    }
  }

  protected final void validateEntity(final T entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Entity is required.");
    }
    validationService.validate(entity);
  }

  private long applyInBatches(
      final Filter filter,
      final ToLongFunction<List<String>> batchOperation,
      final boolean breakOnFirst) {
    long totalAffected = 0;
    final int batchSize = 100;
    String lastId = null;

    while (true) {
      Filter keysetFilter = filter;
      if (lastId != null) {
        keysetFilter =
            filter == null
                ? Filters.gt(BaseEntity.FIELD_ID, lastId)
                : Filters.and(filter, Filters.gt(BaseEntity.FIELD_ID, lastId));
      }

      final Query query =
          new Query()
              .withFilter(keysetFilter)
              .withSort(
                  new com.agentengine.util.common.query.Sort(
                      BaseEntity.FIELD_ID, com.agentengine.util.common.query.Sort.Order.ASC))
              .withIncludeFields(List.of(BaseEntity.FIELD_ID))
              .withPage(new Page(0, batchSize));

      final List<String> batchIds =
          store.findByQuery(query).getItems().stream().map(BaseEntity::getId).toList();

      if (batchIds.isEmpty()) {
        break;
      }

      final long affectedCount = batchOperation.applyAsLong(batchIds);
      totalAffected += affectedCount;

      if (breakOnFirst || batchIds.size() < batchSize) {
        break;
      }
      lastId = batchIds.getLast();
    }
    return totalAffected;
  }

  // Matches the entity only while the stored one is still at its version.
  protected static Filter withIdAndVersionFilter(final BaseEntity entity) {
    return Filters.and(
        Filters.eq(BaseEntity.FIELD_ID, entity.getId()),
        Filters.eq(BaseEntity.FIELD_VERSION, entity.getVersion()));
  }
}
