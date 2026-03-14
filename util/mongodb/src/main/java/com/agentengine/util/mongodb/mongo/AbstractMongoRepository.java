package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.Repository;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;
import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract MongoDB repository implementation providing generic CRUD operations
 *
 * @param <T>
 *          the entity type
 */
public abstract class AbstractMongoRepository<T extends BaseEntity> implements Repository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoRepository.class);

  protected MongoClient mongoClient;
  protected String collectionName;
  protected Class<T> entityClass;
  protected String databaseName;
  protected ValidationService validationService;

  public AbstractMongoRepository(final MongoClientFactory mongoClientFactory, final String collectionName, final Class<T> entityClass,
      final ValidationService validationService) {
    this(mongoClientFactory, "AGENT_ENGINE", collectionName, entityClass, validationService);
  }

  public AbstractMongoRepository(final MongoClientFactory mongoClientFactory, final String databaseName, final String collectionName,
      final Class<T> entityClass, final ValidationService validationService) {
    this.mongoClient = mongoClientFactory.getClient();
    this.databaseName = databaseName;
    this.collectionName = collectionName;
    this.entityClass = entityClass;
    this.validationService = validationService;
  }

  public AbstractMongoRepository(final MongoClient mongoClient, final String collectionName, final Class<T> entityClass) {
    this(mongoClient, "AGENT_ENGINE", collectionName, entityClass, null);
  }

  public AbstractMongoRepository(final MongoClient mongoClient, final String collectionName, final Class<T> entityClass,
      final ValidationService validationService) {
    this(mongoClient, "AGENT_ENGINE", collectionName, entityClass, validationService);
  }

  public AbstractMongoRepository(final MongoClient mongoClient, final String databaseName, final String collectionName,
      final Class<T> entityClass) {
    this(mongoClient, databaseName, collectionName, entityClass, null);
  }

  public AbstractMongoRepository(final MongoClient mongoClient, final String databaseName, final String collectionName,
      final Class<T> entityClass, final ValidationService validationService) {
    this.mongoClient = mongoClient;
    this.databaseName = databaseName;
    this.collectionName = collectionName;
    this.entityClass = entityClass;
    this.validationService = validationService;
  }

  @Override
  public Optional<T> findById(String id) {
    return findById(id, null, null);
  }

  @Override
  public Optional<T> findById(final String id, final List<String> includeFields, final List<String> excludeFields) {
    try {
      T document = getCollection().find(Filters.eq("_id", id)).projection(MongoUtils.getProjection(includeFields, excludeFields)).first();
      if (document != null) {
        return Optional.of(document);
      }
      return Optional.empty();
    } catch (Exception e) {
      LOG.error("Error finding entity by ID: {}", id, e);
      throw new RuntimeException("Error finding entity by ID: " + id, e);
    }
  }

  @Override
  public Map<String, T> findByIds(Collection<String> ids) {
    return findByIds(ids, null, null);
  }

  @Override
  public Map<String, T> findByIds(Collection<String> ids, List<String> includeFields, List<String> excludeFields) {
    try {
      final FindIterable<T> iterable = getCollection().find(Filters.in("_id", ids))
          .projection(MongoUtils.getProjection(includeFields, excludeFields));
      final Map<String, T> result = new HashMap<>();
      for (final T doc : iterable) {
        result.put(doc.getId(), doc);
      }
      return result;
    } catch (Exception e) {
      throw new RuntimeException("Error finding entity by IDs: " + ids, e);
    }
  }

  @Override
  public T insert(T entity) {
    try {
      validateEntity(entity);
      if (StringUtils.isBlank(entity.getId())) {
        entity.setId(new ObjectId().toHexString());
      }
      try {
        getCollection().insertOne(entity);
        return entity;
      } catch (MongoWriteException e) {
        if (e.getError().getCode() == 11000) {
          throw new DuplicateAssetException(entityClass.getSimpleName(), entity.getId());
        }
        LOG.error("Error inserting entity: {}", entity, e);
        throw new RuntimeException("Error saving entity", e);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("Error saving entity: {}", entity, e);
      throw new RuntimeException("Error saving entity", e);
    }
  }

  @Override
  public T update(String id, T entity) {
    return _update(id, entity, false);
  }

  @Override
  public T update(String id, Update update) {
    try {
      final Bson updateOperation = MongoUtils.toBsonUpdate(update);
      final FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
      final T updated = getCollection().findOneAndUpdate(Filters.eq("_id", id), updateOperation, options);
      if (updated != null && validationService != null) {
        validationService.validate(updated);
      }
      return updated;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("Error updating entity: {}", id, e);
      throw new RuntimeException("Error updating entity: " + id, e);
    }
  }

  @Override
  public T save(T entity) {
    try {
      if (StringUtils.isBlank(entity.getId())) {
        return insert(entity);
      }
      return _update(entity.getId(), entity, true);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("Error saving entity: {}", entity, e);
      throw new RuntimeException("Error saving entity", e);
    }
  }

  private T _update(final String id, final T entity, final boolean upsert) {
    try {
      entity.setId(id);
      validateEntity(entity);
      ReplaceOptions options = new ReplaceOptions().upsert(upsert);
      final UpdateResult result = getCollection().replaceOne(Filters.eq("_id", entity.getId()), entity, options);
      if (!upsert && result.getMatchedCount() == 0) {
        throw new AssetNotFoundException(entityClass.getSimpleName(), id);
      }
      return entity;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("Error saving entity: {}", entity, e);
      throw new RuntimeException("Error saving entity", e);
    }
  }

  @Override
  public boolean deleteById(String id) {
    try {
      DeleteResult result = getCollection().deleteOne(Filters.eq("_id", id));
      return result.getDeletedCount() > 0;
    } catch (Exception e) {
      LOG.error("Error deleting entity by ID: {}", id, e);
      throw new RuntimeException("Error deleting entity by ID: " + id, e);
    }
  }

  @Override
  public PaginatedResult<T> findByQuery(final Query query) {
    try {
      final Page page = query == null || query.getPage() == null ? new Page(0, 20) : query.getPage();
      List<T> entities = new ArrayList<>();

      final Bson bsonFilter = MongoUtils.toBson(query == null ? null : query.getFilter());
      final Bson bsonSort = MongoUtils.toSortBson(query == null ? null : query.getSort());
      final Bson projection = MongoUtils.toProjectionBson(query);

      FindIterable<T> iter = getCollection().find(bsonFilter, entityClass);
      if (projection != null) {
        iter = iter.projection(projection);
      }
      iter = iter.skip(page.getOffset()).limit(page.getLimit());
      if (bsonSort != null) {
        iter = iter.sort(bsonSort);
      }

      for (T document : iter) {
        entities.add(document);
      }

      final Long total = query != null && query.isIncludeCount() ? count(bsonFilter) : null;

      return PaginatedResult.create(entities, page, total);
    } catch (Exception e) {
      LOG.error("Error finding all entities in collection: {} with query: {}", collectionName, query, e);
      throw new RuntimeException("Error finding all entities in " + collectionName, e);
    }
  }

  private long count(Bson filter) {
    try {
      return getCollection().countDocuments(filter);
    } catch (Exception e) {
      LOG.error("Error counting entities with filter", e);
      return 0;
    }
  }

  @Override
  public long count() {
    try {
      MongoCollection<T> collection = getCollection();
      return collection.countDocuments();
    } catch (Exception e) {
      LOG.error("Error counting entities", e);
      throw new RuntimeException("Error counting entities", e);
    }
  }

  protected MongoCollection<T> getCollection() {
    return mongoClient.getDatabase(databaseName).getCollection(collectionName, entityClass);
  }

  private void validateEntity(final T entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Entity is required.");
    }
    if (validationService != null) {
      validationService.validate(entity);
    }
  }
}
