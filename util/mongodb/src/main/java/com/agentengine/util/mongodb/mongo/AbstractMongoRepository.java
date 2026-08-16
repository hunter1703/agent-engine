package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.Repository;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract MongoDB repository implementation providing generic CRUD operations.
 *
 * @param <T> the entity type
 */
public abstract class AbstractMongoRepository<T extends BaseEntity>
    extends AbstractMongoReadRepository<T> implements Repository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoRepository.class);
  private static final int DUPLICATE_KEY_ERROR = 11000;

  private final ValidationService validationService;

  public AbstractMongoRepository(
      final MongoClientFactory mongoClientFactory,
      final String collectionName,
      final Class<T> entityClass,
      final ValidationService validationService) {
    this(mongoClientFactory, "AGENT_ENGINE", collectionName, entityClass, validationService);
  }

  public AbstractMongoRepository(
      final MongoClientFactory mongoClientFactory,
      final String databaseName,
      final String collectionName,
      final Class<T> entityClass,
      final ValidationService validationService) {
    super(mongoClientFactory, databaseName, collectionName, entityClass);
    this.validationService = validationService;
  }

  @Override
  public T insert(final T entity) {
    try {
      validateEntity(entity);
      if (StringUtils.isBlank(entity.getId())) {
        entity.setId(new ObjectId().toHexString());
      }
      sanitizeForWrite(entity);
      try {
        getCollection().insertOne(entity);
        return entity;
      } catch (final MongoWriteException exception) {
        throw translateWriteException(exception, entity.getId());
      }
    } catch (final RuntimeException exception) {
      throw exception;
    } catch (final Exception exception) {
      LOG.error("Error inserting entity: {}", entity, exception);
      throw new RuntimeException("Error inserting entity", exception);
    }
  }

  @Override
  public T update(final String id, final T entity) {
    return update(id, null, entity);
  }

  @Override
  public T update(final String id, final Long expectedVersion, final T entity) {
    return replaceEntity(id, expectedVersion, entity, false);
  }

  @Override
  public T update(final String id, final Update update) {
    try {
      final FindOneAndUpdateOptions options =
          new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
      return getCollection()
          .findOneAndUpdate(
              Filters.eq(MongoUtils.FIELD_MONGO_ID, id),
              MongoUtils.toBsonUpdate(sanitizeForWrite(update)),
              options);
    } catch (final RuntimeException exception) {
      throw exception;
    } catch (final Exception exception) {
      LOG.error("Error updating entity: {}", id, exception);
      throw new RuntimeException("Error updating entity: " + id, exception);
    }
  }

  @Override
  public T findOneAndUpdate(final Query query, final Update update) {
    try {
      final FindOneAndUpdateOptions options =
          new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
      final Bson sort = MongoUtils.toSortBson(query.getSort());
      if (sort != null) {
        options.sort(sort);
      }
      return getCollection()
          .findOneAndUpdate(
              MongoUtils.toBson(query.getFilter()),
              MongoUtils.toBsonUpdate(sanitizeForWrite(update)),
              options);
    } catch (final RuntimeException exception) {
      throw exception;
    } catch (final Exception exception) {
      LOG.error("Error in findOneAndUpdate for query: {}", query, exception);
      throw new RuntimeException("Error in findOneAndUpdate", exception);
    }
  }

  @Override
  public long updateOne(final Filter filter, final Update update) {
    try {
      return getCollection()
          .updateOne(MongoUtils.toBson(filter), MongoUtils.toBsonUpdate(sanitizeForWrite(update)))
          .getModifiedCount();
    } catch (final RuntimeException exception) {
      throw exception;
    } catch (final Exception exception) {
      LOG.error("Error in updateOne for filter: {}", filter, exception);
      throw new RuntimeException("Error in updateOne", exception);
    }
  }

  @Override
  public long updateMany(final Filter filter, final Update update) {
    try {
      return getCollection()
          .updateMany(MongoUtils.toBson(filter), MongoUtils.toBsonUpdate(sanitizeForWrite(update)))
          .getModifiedCount();
    } catch (final RuntimeException exception) {
      throw exception;
    } catch (final Exception exception) {
      LOG.error("Error in updateMany for filter: {}", filter, exception);
      throw new RuntimeException("Error in updateMany", exception);
    }
  }

  @Override
  public T save(final T entity) {
    if (entity != null && StringUtils.isBlank(entity.getId())) {
      return insert(entity);
    }
    return replaceEntity(entity == null ? null : entity.getId(), null, entity, true);
  }

  @Override
  public boolean deleteById(final String id) {
    try {
      final DeleteResult result =
          getCollection().deleteOne(Filters.eq(MongoUtils.FIELD_MONGO_ID, id));
      return result.getDeletedCount() > 0;
    } catch (final Exception exception) {
      LOG.error("Error deleting entity by ID: {}", id, exception);
      throw new RuntimeException("Error deleting entity by ID: " + id, exception);
    }
  }

  @Override
  public long deleteByQuery(final Query query) {
    return getCollection().deleteMany(MongoUtils.toBson(query.getFilter())).getDeletedCount();
  }

  private T replaceEntity(
      final String id, final Long expectedVersion, final T entity, final boolean upsert) {
    validateEntity(entity);
    final long currentVersion = entity.getVersion();
    entity.setId(id);
    entity.setVersion(currentVersion + 1);
    sanitizeForWrite(entity);
    try {
      final Bson filter =
          expectedVersion == null
              ? Filters.eq(MongoUtils.FIELD_MONGO_ID, id)
              : Filters.and(
                  Filters.eq(MongoUtils.FIELD_MONGO_ID, id),
                  Filters.eq(BaseEntity.FIELD_VERSION, expectedVersion));
      final UpdateResult result =
          getCollection().replaceOne(filter, entity, new ReplaceOptions().upsert(upsert));
      if (upsert || result.getMatchedCount() > 0) {
        return entity;
      }
      entity.setVersion(currentVersion);
      if (expectedVersion != null) {
        throw new StaleStateException(id, expectedVersion);
      }
      throw new AssetNotFoundException(entityClass.getSimpleName(), id);
    } catch (final MongoWriteException exception) {
      entity.setVersion(currentVersion);
      throw translateWriteException(exception, id);
    } catch (final RuntimeException exception) {
      throw exception;
    } catch (final Exception exception) {
      LOG.error("Error replacing entity: {}", entity, exception);
      throw new RuntimeException("Error replacing entity", exception);
    }
  }

  /**
   * Fills in the fields the repository owns before a partial update is applied, leaving any the
   * caller set explicitly alone. Version is incremented rather than assigned so that concurrent
   * updates cannot lose one another's increments.
   */
  private Update sanitizeForWrite(final Update update) {
    boolean hasUpdatedTime = false;
    boolean hasVersion = false;
    for (final Operation operation : update.operations()) {
      hasUpdatedTime |= BaseEntity.FIELD_UPDATED_TIME.equals(operation.field());
      hasVersion |= BaseEntity.FIELD_VERSION.equals(operation.field());
    }
    if (hasUpdatedTime && hasVersion) {
      return update;
    }
    final List<Operation> operations = new ArrayList<>(update.operations());
    if (!hasUpdatedTime) {
      operations.add(Operation.set(BaseEntity.FIELD_UPDATED_TIME, System.currentTimeMillis()));
    }
    if (!hasVersion) {
      operations.add(Operation.inc(BaseEntity.FIELD_VERSION, 1L));
    }
    return new Update(operations);
  }

  private void sanitizeForWrite(final T entity) {
    final long now = System.currentTimeMillis();
    if (entity.getCreatedTime() == 0) {
      entity.setCreatedTime(now);
    }
    entity.setUpdatedTime(now);
  }

  private RuntimeException translateWriteException(
      final MongoWriteException exception, final String id) {
    if (exception.getError().getCode() == DUPLICATE_KEY_ERROR) {
      return new DuplicateAssetException(entityClass.getSimpleName(), id);
    }
    LOG.error("Error writing entity: {}", id, exception);
    return new RuntimeException("Error writing entity: " + id, exception);
  }

  private void validateEntity(final T entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Entity is required.");
    }
    validationService.validate(entity);
  }
}
