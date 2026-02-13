package com.agentengine.engine.builders.state;

import com.agentengine.engine.api.beans.config.MongoSessionServiceConfig;
import com.agentengine.engine.api.beans.config.SessionServiceConfig.SessionServiceType;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.builders.SessionServiceBuilder;
import com.agentengine.engine.repository.MongoClientFactory;
import com.agentengine.engine.sessions.MongoSessionService;
import com.mongodb.client.MongoClient;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class MongoSessionServiceBuilder
    implements SessionServiceBuilder<MongoSessionServiceConfig, MongoSessionService> {
  private final MongoClientSupport mongoClientSupport;

  public MongoSessionServiceBuilder(final MongoClientSupport mongoClientSupport) {
    this.mongoClientSupport = mongoClientSupport;
  }

  @Override
  public MongoSessionService build(final MongoSessionServiceConfig config) {
    final MongoClient mongoClient = MongoClientFactory.createClient(mongoClientSupport, config.getConnectionString());
    return new MongoSessionService(mongoClient, config.getDatabase(), config.getCollection());
  }

  @Override
  public String type() {
    return SessionServiceType.MONGODB.name().toLowerCase();
  }
}
