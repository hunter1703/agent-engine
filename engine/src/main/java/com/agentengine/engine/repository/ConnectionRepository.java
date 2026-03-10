package com.agentengine.engine.repository;

import com.agentengine.connectors.core.runtime.Connection;
import com.agentengine.util.mongo.MongoClientFactory;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import org.bson.Document;

@Singleton
public class ConnectionRepository {

  private final MongoClient mongoClient;

  @Inject
  public ConnectionRepository(final MongoClientFactory mongoClientFactory) {
    this.mongoClient = mongoClientFactory.getClient();
  }

  @SuppressWarnings("unchecked")
  public Connection findByAppName(final String appName) {
    if (appName == null || appName.isBlank()) {
      return null;
    }
    final Document doc = getCollection().find(Filters.eq("appName", appName)).first();
    if (doc == null) {
      return null;
    }
    final Object inputsObj = doc.get("inputs");
    final Map<String, Object> inputs =
        inputsObj instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : Map.of();
    return new Connection(doc.getString("appName"), inputs);
  }

  private MongoCollection<Document> getCollection() {
    return mongoClient.getDatabase("AGENT_ENGINE").getCollection("Connection", Document.class);
  }
}
