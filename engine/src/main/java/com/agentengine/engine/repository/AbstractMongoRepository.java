package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.query.Page;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.api.update.Update;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.utils.MongoUtils;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract MongoDB repository implementation providing generic CRUD operations
 *
 * @param <T> the entity type
 */
public abstract class AbstractMongoRepository<T extends BaseEntity> implements Repository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoRepository.class);

  protected MongoClient mongoClient;
  protected String collectionName;
  protected Class<T> entityClass;
  protected String databaseName;

  protected AbstractMongoRepository() {
    this.mongoClient = null;
    this.collectionName = null;
    this.entityClass = null;
    this.databaseName = null;
  }

  public AbstractMongoRepository(
      final MongoClientFactory mongoClientFactory, String collectionName, Class<T> entityClass) {
    this(mongoClientFactory, "AGENT_ENGINE", collectionName, entityClass);
  }

  public AbstractMongoRepository(
      final MongoClientFactory mongoClientFactory,
      String databaseName,
      String collectionName,
      Class<T> entityClass) {
    this.mongoClient = mongoClientFactory.getClient();
    this.databaseName = databaseName;
    this.collectionName = collectionName;
    this.entityClass = entityClass;
  }

  public AbstractMongoRepository(
      final MongoClient mongoClient, String collectionName, Class<T> entityClass) {
    this(mongoClient, "AGENT_ENGINE", collectionName, entityClass);
  }

  public AbstractMongoRepository(
      final MongoClient mongoClient,
      String databaseName,
      String collectionName,
      Class<T> entityClass) {
    this.mongoClient = mongoClient;
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
      final FindOneAndUpdateOptions options =
          new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
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
      throw new RuntimeException("Error deleting entity by ID: " + id, e);
    }
  }

  @Override
  public PaginatedResult<T> findByQuery(final Query query) {
    try {
      final Page page = query != null && query.getPage() != null ? query.getPage() : new Page();
      List<T> entities = new ArrayList<>();

      Bson bsonFilter = MongoQueryAdapter.toBson(query == null ? null : query.getFilter());

      final FindIterable<T> iter =
          getCollection()
              .find(bsonFilter, entityClass)
              .skip(page.getOffset())
              .limit(page.getLimit());

      for (T document : iter) {
        entities.add(document);
      }

      long total = count(bsonFilter);

      return PaginatedResult.create(entities, page, total);
    } catch (Exception e) {
      LOG.error(
          "Error finding all entities in collection: {} with query: {}", collectionName, query, e);
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
}
