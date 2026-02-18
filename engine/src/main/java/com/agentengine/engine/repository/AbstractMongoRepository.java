package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.update.Update;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.utils.MongoUtils;
import com.agentengine.engine.utils.Page;
import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.engine.utils.Query;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.DeleteResult;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abstract MongoDB repository implementation providing generic CRUD operations
 *
 * @param <T>
 *          the entity type
 */
public abstract class AbstractMongoRepository<T extends BaseEntity> implements Repository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoRepository.class);

  protected final MongoClient mongoClient;
  protected final String collectionName;
  protected final Class<T> entityClass;
  private final String databaseName;

  public AbstractMongoRepository(final MongoClientSupport mongoClientSupport, String collectionName,
      Class<T> entityClass) {
    this(mongoClientSupport, "AGENT_ENGINE", collectionName, entityClass);
  }

  public AbstractMongoRepository(final MongoClientSupport mongoClientSupport, String databaseName, String collectionName,
                                 Class<T> entityClass) {
    this.mongoClient = createClient(mongoClientSupport);
    this.databaseName = databaseName;
    this.collectionName = collectionName;
    this.entityClass = entityClass;
  }

  @Override
  public Optional<T> findById(String id) {
    try {
      T document = getCollection().find(Filters.eq("_id", id)).first();
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
  public T insert(T entity) {
    try {
      if (StringUtils.isBlank(entity.getId())) {
        entity.setId(new ObjectId().toHexString());
      }
      getCollection().insertOne(entity);
      return entity;
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
      return getCollection().findOneAndUpdate(Filters.eq("_id", id), updateOperation, options);
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
    } catch (Exception e) {
      LOG.error("Error saving entity: {}", entity, e);
      throw new RuntimeException("Error saving entity", e);
    }
  }

  private T _update(final String id, final T entity, final boolean upsert) {
    try {
      entity.setId(id);
      ReplaceOptions options = new ReplaceOptions().upsert(upsert);
      getCollection().replaceOne(Filters.eq("_id", entity.getId()), entity, options);
      return entity;
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
      throw new RuntimeException(STR."Error deleting entity by ID: \{id}", e);
    }
  }

  @Override
  public PaginatedResult<T> findByQuery(final Query query) {
    try {
      Page page = query == null ? null : query.getPage();
      page = page == null ? new Page() : page;
      List<T> entities = new ArrayList<>();
      final FindIterable<T> iter = query != null && query.getFilter() != null
          ? getCollection().find(query.getFilter(), entityClass).skip(page.getOffset()).limit(page.getLimit())
          : getCollection().find(entityClass).skip(page.getOffset()).limit(page.getLimit());
      for (T document : iter) {
        entities.add(document);
      }
      return PaginatedResult.create(entities, page);
    } catch (Exception e) {
      throw new RuntimeException("Error finding all entities", e);
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

  private MongoClient createClient(MongoClientSupport mongoClientSupport) {
    return MongoClientFactory.createClient(mongoClientSupport, null);
  }
}
