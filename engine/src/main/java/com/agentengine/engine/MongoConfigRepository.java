package com.agentengine.engine;

import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.beans.config.ModelConfig;
import com.agentengine.engine.utils.StringUtils;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;

@Singleton
public final class MongoConfigRepository implements ConfigRepository {
  private static final Logger LOGGER = Logger.getLogger(MongoConfigRepository.class.getName());
  private static final String AGENT_COLLECTION = "Agent";
  private static final String MODEL_COLLECTION = "Model";
  private final MongoClient mongoClient;

  @Inject
  public MongoConfigRepository() {
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

  private static MongoClient createClient() {
    final ConnectionString connectionString = new ConnectionString(
        System.getProperty("MONGODB_CONNECTION_STRING", "mongodb://localhost:27000"));
    MongoClientSettings mongoClientSettings = MongoClientSettings.builder().applicationName("agent-engine")
        .applyConnectionString(connectionString).build();
    return MongoClients.create(mongoClientSettings);
  }
}
