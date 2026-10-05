package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.annotations.Indexed;
import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.repository.EntityStore;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.utils.ExceptionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.utils.Utils;
import com.agentengine.util.context.Context;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The MongoDB storage of one entity class, as {@link MongoBackend#getEntityStore} returns it. */
public final class MongoEntityStore<T extends BaseEntity> implements EntityStore<T> {

  private static final Logger LOG = LoggerFactory.getLogger(MongoEntityStore.class);
  private static final int DUPLICATE_KEY_ERROR = 11000;
  private static final int INDEX_NOT_FOUND = 27;

  private final DocumentRepositorySpec<T> spec;
  private final MongoClientFactory clientFactory;
  private final String collectionName;

  public MongoEntityStore(
      final DocumentRepositorySpec<T> spec, final MongoClientFactory clientFactory) {
    this.spec = spec;
    this.clientFactory = clientFactory;
    this.collectionName =
        StringUtils.isNotBlank(spec.collectionName())
            ? spec.collectionName()
            : spec.entityClass().getSimpleName();
  }

  public DocumentRepositorySpec<T> spec() {
    return spec;
  }

  @Override
  public Class<T> entityClass() {
    return spec.entityClass();
  }

  @Override
  public String newId() {
    return new ObjectId().toHexString();
  }

  @Override
  public T insert(final T entity) {
    if (StringUtils.isBlank(entity.getId())) {
      entity.setId(newId());
    }
    try {
      collection().insertOne(entity);
      return entity;
    } catch (final MongoWriteException exception) {
      throw translateWriteException(exception, entity.getId());
    } catch (final Exception exception) {
      LOG.error("Error inserting entity: {}", entity, exception);
      throw ExceptionUtils.wrapInRuntimeException(exception, "Error inserting entity");
    }
  }

  @Override
  public List<T> insertMany(final List<T> entities) {
    try {
      for (final T entity : entities) {
        if (StringUtils.isBlank(entity.getId())) {
          entity.setId(newId());
        }
      }
      collection().insertMany(entities);
      return entities;
    } catch (final MongoBulkWriteException exception) {
      throw translateBulkWriteException(exception, entities);
    } catch (final Exception exception) {
      LOG.error("Error inserting entities: {}", entities, exception);
      throw ExceptionUtils.wrapInRuntimeException(exception, "Error inserting entities");
    }
  }

  @Override
  public T findById(
      final String id, final List<String> includeFields, final List<String> excludeFields) {
    try {
      return collection()
          .find(Filters.eq(MongoUtils.FIELD_MONGO_ID, id))
          .projection(MongoUtils.getProjection(includeFields, excludeFields))
          .first();
    } catch (final Exception exception) {
      LOG.error("Error finding entity by ID: {}", id, exception);
      throw new RuntimeException("Error finding entity by ID: " + id, exception);
    }
  }

  @Override
  public Map<String, T> findByIds(
      final Collection<String> ids,
      final List<String> includeFields,
      final List<String> excludeFields) {
    try {
      final FindIterable<T> iterable =
          collection()
              .find(Filters.in(MongoUtils.FIELD_MONGO_ID, ids))
              .projection(MongoUtils.getProjection(includeFields, excludeFields));
      final Map<String, T> result = new HashMap<>();
      for (final T document : iterable) {
        result.put(document.getId(), document);
      }
      return result;
    } catch (final Exception exception) {
      throw new RuntimeException("Error finding entity by IDs: " + ids, exception);
    }
  }

  @Override
  public PaginatedResult<T> findByQuery(final Query query) {
    try {
      final Page page = query == null ? new Page(0, 20) : query.getPage();
      final List<T> entities = new ArrayList<>();

      final Bson bsonFilter = MongoUtils.toBson(query == null ? null : query.getFilter());

      if (page.getLimit() != 0) {
        final Bson bsonSort = MongoUtils.toSortBson(query == null ? null : query.getSorts());
        final Bson projection = MongoUtils.toProjectionBson(query);
        FindIterable<T> iterable = collection().find(bsonFilter, spec.entityClass());
        if (projection != null) {
          iterable = iterable.projection(projection);
        }
        iterable = iterable.skip(page.getOffset());
        if (page.getLimit() > 0) {
          iterable = iterable.limit(page.getLimit());
        }
        if (bsonSort != null) {
          iterable = iterable.sort(bsonSort);
        }

        for (final T document : iterable) {
          entities.add(document);
        }
      }

      final Long total = query != null && query.isIncludeCount() ? count(bsonFilter) : null;
      return PaginatedResult.create(entities, page, total);
    } catch (final Exception exception) {
      LOG.error(
          "Error finding all entities in collection: {} with query: {}",
          collectionName,
          query,
          exception);
      throw new RuntimeException("Error finding all entities in " + collectionName, exception);
    }
  }

  /**
   * Replaces the stored entity in one write that keeps the stored access list, so an access list
   * applied since the entity was read is never overwritten. Upserts only when no version is
   * expected: an entity at another version is stale, not missing.
   */
  @Override
  public T replace(
      final T entity, final Long expectedVersion, final boolean upsert, final T existing) {
    try {
      final List<Bson> conditions = new ArrayList<>();
      conditions.add(Filters.eq(MongoUtils.FIELD_MONGO_ID, entity.getId()));
      if (expectedVersion != null) {
        conditions.add(Filters.eq(BaseEntity.FIELD_VERSION, expectedVersion));
      }
      final UpdateResult result =
          collection()
              .replaceOne(
                  Filters.and(conditions),
                  entity,
                  new ReplaceOptions().upsert(upsert && expectedVersion == null));
      if (result.getMatchedCount() > 0 || result.getUpsertedId() != null) {
        return entity;
      }
      if (expectedVersion != null) {
        throw new StaleStateException(entity.getId(), expectedVersion);
      }
      throw new AssetNotFoundException(collectionName, entity.getId());
    } catch (final MongoWriteException exception) {
      throw translateWriteException(exception, entity.getId());
    } catch (final Exception exception) {
      LOG.error("Error replacing entity: {}", entity, exception);
      throw ExceptionUtils.wrapInRuntimeException(exception, "Error replacing entity");
    }
  }

  @Override
  public T findOneAndUpdate(final Query query, final Update update) {
    try {
      final FindOneAndUpdateOptions options =
          new FindOneAndUpdateOptions()
              .returnDocument(ReturnDocument.AFTER)
              .projection(MongoUtils.toProjectionBson(query));
      final Bson sort = MongoUtils.toSortBson(query.getSorts());
      if (sort != null) {
        options.sort(sort);
      }
      return collection()
          .findOneAndUpdate(
              MongoUtils.toBson(query.getFilter()), MongoUtils.toBsonUpdate(update), options);
    } catch (final Exception exception) {
      LOG.error("Error in findOneAndUpdate for query: {}", query, exception);
      throw ExceptionUtils.wrapInRuntimeException(exception, "Error in findOneAndUpdate");
    }
  }

  @Override
  public long updateOne(final Filter filter, final Update update) {
    try {
      return collection()
          .updateOne(MongoUtils.toBson(filter), MongoUtils.toBsonUpdate(update))
          .getModifiedCount();
    } catch (final Exception exception) {
      LOG.error("Error in updateOne for filter: {}", filter, exception);
      throw ExceptionUtils.wrapInRuntimeException(exception, "Error in updateOne");
    }
  }

  @Override
  public T upsertOne(final Filter filter, final Update update) {
    try {
      return collection()
          .findOneAndUpdate(
              MongoUtils.toBson(filter),
              MongoUtils.toBsonUpdate(update),
              new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
    } catch (final Exception exception) {
      LOG.error("Error in upsertOne for filter: {}", filter, exception);
      throw ExceptionUtils.wrapInRuntimeException(exception, "Error in upsertOne");
    }
  }

  @Override
  public long updateMany(final Filter filter, final Update update) {
    try {
      return collection()
          .updateMany(MongoUtils.toBson(filter), MongoUtils.toBsonUpdate(update))
          .getModifiedCount();
    } catch (final Exception exception) {
      LOG.error("Error in updateMany for filter: {}", filter, exception);
      throw ExceptionUtils.wrapInRuntimeException(exception, "Error in updateMany");
    }
  }

  @Override
  public boolean deleteById(final String id) {
    try {
      final DeleteResult result = collection().deleteOne(Filters.eq(MongoUtils.FIELD_MONGO_ID, id));
      return result.getDeletedCount() > 0;
    } catch (final Exception exception) {
      LOG.error("Error deleting entity by ID: {}", id, exception);
      throw new RuntimeException("Error deleting entity by ID: " + id, exception);
    }
  }

  @Override
  public boolean delete(final String id, final long version) {
    return collection()
            .deleteOne(
                Filters.and(
                    Filters.eq(MongoUtils.FIELD_MONGO_ID, id),
                    Filters.eq(BaseEntity.FIELD_VERSION, version)))
            .getDeletedCount()
        > 0;
  }

  @Override
  public long deleteByFilter(final Filter filter) {
    return collection().deleteMany(MongoUtils.toBson(filter)).getDeletedCount();
  }

  /**
   * Applies the entity's {@link Index} and {@link Indexed} declarations to the current customer's
   * collection — the shared one for a global store: missing indexes are created, and those declared
   * with {@code drop = true} are dropped. Creating an index that already exists with the same
   * definition is a no-op in MongoDB, so this is safe to repeat.
   */
  @Override
  public void setup() {
    final MongoCollection<T> collection = collection();
    final List<IndexModel> models = new ArrayList<>();
    for (final Index declaration : spec.entityClass().getAnnotationsByType(Index.class)) {
      if (declaration.drop()) {
        dropIndex(collection, declaration);
        continue;
      }
      models.add(new IndexModel(Document.parse(declaration.def()), toIndexOptions(declaration)));
    }
    for (final Field field : Utils.fieldsAnnotatedWith(spec.entityClass(), Indexed.class)) {
      final Indexed declaration = field.getAnnotation(Indexed.class);
      final IndexOptions options = new IndexOptions();
      if (!declaration.name().isBlank()) {
        options.name(declaration.name());
      }
      models.add(new IndexModel(Indexes.ascending(field.getName()), options));
    }
    if (spec.entityClass().isAnnotationPresent(Permissioned.class)) {
      // Access checks filter by grant.
      models.add(new IndexModel(Indexes.ascending(BaseEntity.FIELD_ACL_GRANTS)));
    }
    if (models.isEmpty()) {
      return;
    }
    final List<String> created = collection.createIndexes(models);
    LOG.info("Ensured {} index(es) on collection {}: {}", created.size(), collectionName, created);
  }

  /** The driver's collection for the current customer. */
  private MongoCollection<T> collection() {
    return collection(currentCustomerId());
  }

  private MongoCollection<T> collection(final String customerId) {
    final String database =
        spec.global() ? spec.clientType().name() : spec.clientType().name() + "_" + customerId;
    return clientFactory
        .getClient(spec.clientType(), customerId)
        .getDatabase(database)
        .getCollection(collectionName, spec.entityClass());
  }

  private String currentCustomerId() {
    return spec.global() ? null : Context.currentCustomerId().orElseThrow();
  }

  private RuntimeException translateWriteException(
      final MongoWriteException exception, final String id) {
    if (exception.getError().getCode() == DUPLICATE_KEY_ERROR) {
      return new DuplicateAssetException(collectionName, id);
    }
    LOG.error("Error writing entity: {}", id, exception);
    return new RuntimeException("Error writing entity: " + id, exception);
  }

  private RuntimeException translateBulkWriteException(
      final MongoBulkWriteException exception, final List<T> entities) {
    for (final BulkWriteError error : exception.getWriteErrors()) {
      if (error.getCode() == DUPLICATE_KEY_ERROR) {
        return new DuplicateAssetException(collectionName, entities.get(error.getIndex()).getId());
      }
    }
    LOG.error("Error writing entities: {}", entities, exception);
    return new RuntimeException("Error writing entities", exception);
  }

  private void dropIndex(final MongoCollection<T> collection, final Index declaration) {
    if (declaration.name().isBlank()) {
      throw new IllegalArgumentException(
          "Index on "
              + collectionName
              + " must declare a name to be dropped: "
              + declaration.def());
    }
    try {
      collection.dropIndex(declaration.name());
      LOG.info("Dropped index {} on collection {}", declaration.name(), collectionName);
    } catch (final MongoCommandException exception) {
      if (exception.getErrorCode() != INDEX_NOT_FOUND) {
        throw exception;
      }
    }
  }

  private static IndexOptions toIndexOptions(final Index declaration) {
    final IndexOptions options = new IndexOptions();
    if (!declaration.name().isBlank()) {
      options.name(declaration.name());
    }
    if (declaration.unique()) {
      options.unique(true);
    }
    if (declaration.expireAfterSeconds() >= 0) {
      options.expireAfter(declaration.expireAfterSeconds(), TimeUnit.SECONDS);
    }
    if (!declaration.partialFilterExpression().isBlank()) {
      options.partialFilterExpression(Document.parse(declaration.partialFilterExpression()));
    }
    return options;
  }

  private long count(final Bson filter) {
    try {
      return collection().countDocuments(filter);
    } catch (final Exception exception) {
      LOG.error("Error counting entities with filter", exception);
      return 0;
    }
  }
}
