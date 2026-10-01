package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.EntityUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.validation.ValidationService;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
  public final T insert(final T entity) {
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
  public final T update(final String id, final T entity) {
    return replace(id, entity == null ? null : entity.getVersion(), entity, false);
  }

  @Override
  public final T save(final T entity) {
    return entity != null && StringUtils.isBlank(entity.getId())
        ? insert(entity)
        : replace(
            entity == null ? null : entity.getId(),
            entity == null ? null : entity.getVersion(),
            entity,
            true);
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

  @Override
  public boolean delete(final T entity) {
    return deleteFromStore(entity);
  }

  @Override
  public boolean deleteByIdIgnoringVersion(final String id) {
    return deleteFromStore(id);
  }

  @Override
  public void deleteByFilterIgnoringVersion(final Filter filter) {
    store.deleteByFilter(filter);
    publish(new EntityChange.Matching<>(EntityChange.Type.DELETED, filter));
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
  protected final List<T> storeNew(final List<T> entities) {
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
  protected final T updateFirst(final Query query, final Update update) {
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
    final Update applicable = EntityUtils.prepareUpdate(update, contextualFields());
    if (applicable.operations().isEmpty()) {
      return 0;
    }
    final long updated =
        one ? store.updateOne(filter, applicable) : store.updateMany(filter, applicable);
    if (updated > 0) {
      publish(new EntityChange.Matching<>(EntityChange.Type.UPDATED, filter));
    }
    return updated;
  }

  /**
   * Applies {@code update} to the first entity {@code filter} matches or, when none does, to a new
   * one made of {@code filter}'s equality conditions, and returns it after the update. A new entity
   * is created without going through {@link #create}.
   */
  protected final T upsertOne(final Filter filter, final Update update) {
    final List<Operation> operations =
        new ArrayList<>(EntityUtils.prepareUpdate(update, contextualFields()).operations());
    operations.add(
        Operation.setOnInsert(BaseEntity.FIELD_CREATED_TIME, System.currentTimeMillis()));
    final T upserted = store.upsertOne(filter, new Update(operations));
    publish(
        new EntityChange.Entities<>(EntityChange.Type.UPDATED, Map.of(upserted.getId(), upserted)));
    return upserted;
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
  protected final T write(
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
    final T written = store.replace(entity, versionToExpect, upsert);
    publish(
        new EntityChange.Entities<>(
            existing == null ? EntityChange.Type.CREATED : EntityChange.Type.UPDATED,
            Map.of(id, written)));
    return written;
  }

  /** Deletes the entity with id {@code id}, whatever its version, and publishes the delete. */
  protected final boolean deleteFromStore(final String id) {
    final boolean deleted = store.deleteById(id);
    if (deleted) {
      publish(new EntityChange.Ids<>(EntityChange.Type.DELETED, Set.of(id)));
    }
    return deleted;
  }

  /** Deletes the entity while it is still at its version, and publishes the delete. */
  protected final boolean deleteFromStore(final T entity) {
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

  // Matches the entity only while the stored one is still at its version.
  protected static Filter withIdAndVersionFilter(final BaseEntity entity) {
    return Filters.and(
        Filters.eq(BaseEntity.FIELD_ID, entity.getId()),
        Filters.eq(BaseEntity.FIELD_VERSION, entity.getVersion()));
  }
}
