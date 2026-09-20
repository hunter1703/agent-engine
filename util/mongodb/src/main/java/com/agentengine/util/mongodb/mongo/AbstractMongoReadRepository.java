package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.Utils;
import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.annotations.Indexed;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.context.Context;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.ReadRepository;
import com.mongodb.MongoCommandException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractMongoReadRepository<T extends BaseEntity>
    implements ReadRepository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoReadRepository.class);
  private static final int INDEX_NOT_FOUND = 27;

  protected final MongoClientFactory mongoClientFactory;
  protected final MongoStoreClientType clientType;
  private final String collectionName;
  protected final Class<T> entityClass;

  public AbstractMongoReadRepository(
      final MongoClientFactory mongoClientFactory,
      final MongoStoreClientType clientType,
      final Class<T> entityClass) {
    this.mongoClientFactory = mongoClientFactory;
    this.clientType = clientType;
    this.collectionName = entityClass.getSimpleName();
    this.entityClass = entityClass;
  }

  @Override
  public T findById(final String id) {
    return findById(id, null, null);
  }

  @Override
  public T findById(
      final String id, final List<String> includeFields, final List<String> excludeFields) {
    try {
      return getCollection()
          .find(Filters.eq(MongoUtils.FIELD_MONGO_ID, id))
          .projection(MongoUtils.getProjection(includeFields, excludeFields))
          .first();
    } catch (final Exception exception) {
      LOG.error("Error finding entity by ID: {}", id, exception);
      throw new RuntimeException("Error finding entity by ID: " + id, exception);
    }
  }

  @Override
  public Map<String, T> findByIds(final Collection<String> ids) {
    return findByIds(ids, null, null);
  }

  @Override
  public Map<String, T> findByIds(
      final Collection<String> ids,
      final List<String> includeFields,
      final List<String> excludeFields) {
    try {
      final FindIterable<T> iterable =
          getCollection()
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
        FindIterable<T> iterable = getCollection().find(bsonFilter, entityClass);
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

  @Override
  public long count() {
    try {
      return getCollection().countDocuments();
    } catch (final Exception exception) {
      LOG.error("Error counting entities", exception);
      throw new RuntimeException("Error counting entities", exception);
    }
  }

  public MongoStoreClientType clientType() {
    return clientType;
  }

  public String collectionName() {
    return collectionName;
  }

  /**
   * Prepares the current customer's collection for the entity by applying its {@link Index} and
   * {@link Indexed} declarations: missing indexes are created, and those declared with {@code drop
   * = true} are dropped. Creating an index that already exists with the same definition is a no-op
   * in MongoDB, so this is safe to call repeatedly.
   */
  protected void setup() {
    final List<IndexModel> models = new ArrayList<>();
    for (final Index declaration : entityClass.getAnnotationsByType(Index.class)) {
      if (declaration.drop()) {
        dropIndex(declaration);
        continue;
      }
      models.add(new IndexModel(Document.parse(declaration.def()), toIndexOptions(declaration)));
    }
    for (final Field field : Utils.fieldsAnnotatedWith(entityClass, Indexed.class)) {
      final Indexed declaration = field.getAnnotation(Indexed.class);
      final IndexOptions options = new IndexOptions();
      if (!declaration.name().isBlank()) {
        options.name(declaration.name());
      }
      models.add(new IndexModel(Indexes.ascending(field.getName()), options));
    }
    if (models.isEmpty()) {
      return;
    }
    final List<String> created = getCollection().createIndexes(models);
    LOG.info("Ensured {} index(es) on collection {}: {}", created.size(), collectionName, created);
  }

  protected String database() {
    return clientType.name() + "_" + customerId();
  }

  protected Integer customerId() {
    return Context.customerId().orElseThrow();
  }

  protected final MongoCollection<T> getCollection() {
    return mongoClientFactory.getClient(clientType, customerId()).getDatabase(database()).getCollection(collectionName, entityClass);
  }

  private void dropIndex(final Index declaration) {
    if (declaration.name().isBlank()) {
      throw new IllegalArgumentException(
          "Index on "
              + entityClass.getSimpleName()
              + " must declare a name to be dropped: "
              + declaration.def());
    }
    try {
      getCollection().dropIndex(declaration.name());
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
      return getCollection().countDocuments(filter);
    } catch (final Exception exception) {
      LOG.error("Error counting entities with filter", exception);
      return 0;
    }
  }
}
