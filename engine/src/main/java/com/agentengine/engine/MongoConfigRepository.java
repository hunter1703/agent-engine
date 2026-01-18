package com.agentengine.engine;

import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ModelConfig;
import com.agentengine.engine.utils.CollectionUtils;
import com.agentengine.engine.utils.StringUtils;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PojoCodecProvider;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Singleton
public final class MongoConfigRepository implements ConfigRepository {
  private static final Logger LOGGER = Logger.getLogger(MongoConfigRepository.class.getName());
  private static final String AGENT_COLLECTION = "Agent";
  private static final String MODEL_COLLECTION = "Model";
  private final MongoClient mongoClient;
  private final MongoClientSupport mongoClientSupport;

  @Inject
  public MongoConfigRepository(final MongoClientSupport mongoClientSupport) {
    this.mongoClientSupport = mongoClientSupport;
    this.mongoClient = createClient();
  }

  @Override
  public AgentConfig loadAgentConfig(final String agentName) {
    if (StringUtils.isBlank(agentName)) {
      return null;
    }
    return findDocument(AGENT_COLLECTION, agentName, AgentConfig.class);
  }

  @Override
  public ModelConfig loadModelConfig(final String modelName) {
    if (StringUtils.isBlank(modelName)) {
      return null;
    }
    return findDocument(MODEL_COLLECTION, modelName, ModelConfig.class);
  }

  private <T> T findDocument(final String collectionName, final String id, final Class<T> clazz) {
    try {
      MongoCollection<Document> collection = mongoClient.getDatabase("AGENT_ENGINE").getCollection(collectionName);
        return collection.find(Filters.eq("_id", id), clazz).limit(1).first();
    } catch (Exception ex) {
      LOGGER.log(Level.WARNING, "Failed to read config from MongoDB", ex);
      return null;
    }
  }

  private MongoClient createClient() {
    final String fromEnv = System.getenv("MONGODB_CONNECTION_STRING");
    final String connectionValue = StringUtils.isNotBlank(fromEnv) ? fromEnv : "mongodb://localhost:27000";
    return MongoClients.create(buildClientSettings(connectionValue, getBsonDiscriminators()));
  }

  private List<String> getBsonDiscriminators() {
    return CollectionUtils.nullSafeList(mongoClientSupport.getBsonDiscriminators());
  }

  static MongoClientSettings buildClientSettings(final String connectionValue, final List<String> bsonDiscriminators) {
    final ConnectionString connectionString = new ConnectionString(connectionValue);
    PojoCodecProvider.Builder pojoCodecProviderBuilder = PojoCodecProvider.builder().automatic(true);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    for (String discriminator : CollectionUtils.nullSafeList(bsonDiscriminators)) {
      try {
        pojoCodecProviderBuilder.register(ClassModel.builder(Class.forName(discriminator, true, classLoader))
            .enableDiscriminator(true)
            .build());
      } catch (ClassNotFoundException ex) {
        // Ignore
      }
    }
    CodecRegistry codecRegistry = fromRegistries(
        MongoClientSettings.getDefaultCodecRegistry(),
        fromProviders(pojoCodecProviderBuilder.build()));
    return MongoClientSettings.builder().applicationName("agent-engine")
        .applyConnectionString(connectionString)
        .codecRegistry(codecRegistry)
        .build();
  }
}
