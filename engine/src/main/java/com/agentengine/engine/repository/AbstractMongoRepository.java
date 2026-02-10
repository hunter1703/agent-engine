package com.agentengine.engine.repository;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.utils.Page;
import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.engine.utils.Query;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import dev.langchain4j.agent.tool.P;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Abstract MongoDB repository implementation providing generic CRUD operations
 *
 * @param <T>
 *          the entity type
 */
public abstract class AbstractMongoRepository<T> implements Repository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoRepository.class);

  protected final MongoClient mongoClient;
  protected final String collectionName;
  protected final Class<T> entityClass;

  public AbstractMongoRepository(final MongoClientSupport mongoClientSupport, String collectionName,
      Class<T> entityClass) {
    this.mongoClient = createClient(mongoClientSupport);
    this.collectionName = collectionName;
    this.entityClass = entityClass;
  }

  protected String getIdField() {
    return "_id";
  }

  /**
   * Extract the ID from an entity
   *
   * @param entity
   *          the entity
   * @return the ID
   */
  protected abstract String getId(T entity);

  @Override
  public Optional<T> findById(String id) {
    try {
      MongoCollection<Document> collection = getCollection();
      Document document = collection.find(Filters.eq(getIdField(), id)).first();
      if (document != null) {
        return Optional.of(fromDocument(document));
      }
      return Optional.empty();
    } catch (Exception e) {
      LOG.error("Error finding entity by ID: {}", id, e);
      throw new RuntimeException("Error finding entity by ID: " + id, e);
    }
  }

  @Override
  public T save(T entity) {
    try {
      MongoCollection<Document> collection = getCollection();
      Document document = toDocument(entity);
      String id = getId(entity);

      // If ID field is not set in the document, add it
      if (!document.containsKey(getIdField()) && id != null) {
        document.put(getIdField(), id);
      }

      ReplaceOptions options = new ReplaceOptions().upsert(true);
      collection.replaceOne(Filters.eq(getIdField(), id), document, options);
      return entity;
    } catch (Exception e) {
      LOG.error("Error saving entity: {}", entity, e);
      throw new RuntimeException("Error saving entity", e);
    }
  }

  @Override
  public boolean deleteById(String id) {
    try {
      MongoCollection<Document> collection = getCollection();
      DeleteResult result = collection.deleteOne(Filters.eq(getIdField(), id));
      return result.getDeletedCount() > 0;
    } catch (Exception e) {
      LOG.error("Error deleting entity by ID: {}", id, e);
      throw new RuntimeException("Error deleting entity by ID: " + id, e);
    }
  }

  @Override
  public PaginatedResult<T> findByQuery(final Query query) {
    try {
      Page page = query == null ? null : query.getPage();
      page = page == null ? new Page() : page;
      List<T> entities = new ArrayList<>();
      for (Document document : getCollection().find().skip(page.getOffset()).limit(page.getLimit())) {
        entities.add(fromDocument(document));
      }
      return PaginatedResult.create(entities, page);
    } catch (Exception e) {
      throw new RuntimeException("Error finding all entities", e);
    }
  }

  @Override
  public long count() {
    try {
      MongoCollection<Document> collection = getCollection();
      return collection.countDocuments();
    } catch (Exception e) {
      LOG.error("Error counting entities", e);
      throw new RuntimeException("Error counting entities", e);
    }
  }

  /**
   * Convert an entity to a MongoDB Document
   *
   * @param entity
   *          the entity
   * @return the document representation
   */
  protected Document toDocument(T entity) {
    String json = JsonUtils.toJson(entity);
    return Document.parse(json);
  }

  /**
   * Convert a MongoDB Document to an entity
   *
   * @param document
   *          the document
   * @return the entity
   */
  protected T fromDocument(Document document) {
    String json = document.toJson();
    return JsonUtils.fromJson(json, entityClass);
  }

  /**
   * Get the collection for this repository
   *
   * @return the MongoDB collection
   */
  protected MongoCollection<Document> getCollection() {
    return mongoClient.getDatabase("AGENT_ENGINE").getCollection(collectionName);
  }

  private MongoClient createClient(MongoClientSupport mongoClientSupport) {
    final String fromEnv = System.getenv("MONGODB_CONNECTION_STRING");
    final String connectionValue = StringUtils.isNotBlank(fromEnv) ? fromEnv : "mongodb://localhost:27002";
    return MongoClients.create(buildClientSettings(connectionValue, getBsonDiscriminators(mongoClientSupport)));
  }

  private List<String> getBsonDiscriminators(MongoClientSupport mongoClientSupport) {
    return CollectionUtils.nullSafeList(mongoClientSupport.getBsonDiscriminators());
  }

  static MongoClientSettings buildClientSettings(final String connectionValue, final List<String> bsonDiscriminators) {
    final ConnectionString connectionString = new ConnectionString(connectionValue);
    PojoCodecProvider.Builder pojoCodecProviderBuilder = PojoCodecProvider.builder().automatic(true);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    for (String discriminator : CollectionUtils.nullSafeList(bsonDiscriminators)) {
      try {
        pojoCodecProviderBuilder.register(
            ClassModel.builder(Class.forName(discriminator, true, classLoader)).enableDiscriminator(true).build());
      } catch (ClassNotFoundException ex) {
        // Ignore
      }
    }
    CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
        fromProviders(pojoCodecProviderBuilder.build()));
    return MongoClientSettings.builder().applicationName("agent-engine").applyConnectionString(connectionString)
        .codecRegistry(codecRegistry).build();
  }
}