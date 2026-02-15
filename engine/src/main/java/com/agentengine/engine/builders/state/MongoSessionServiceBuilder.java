package com.agentengine.engine.builders.state;

import com.agentengine.engine.api.beans.config.MongoSessionServiceConfig;
import com.agentengine.engine.api.beans.config.SessionServiceConfig.SessionServiceType;
import com.agentengine.engine.api.builders.SessionServiceBuilder;
import com.agentengine.engine.sessions.MongoSessionService;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.inject.Singleton;

@Singleton
public class MongoSessionServiceBuilder
    implements
      SessionServiceBuilder<MongoSessionServiceConfig, MongoSessionService> {
  private final MongoClientSupport mongoClientSupport;

  public MongoSessionServiceBuilder(final MongoClientSupport mongoClientSupport) {
    this.mongoClientSupport = mongoClientSupport;
  }

  @Override
  public MongoSessionService build(final MongoSessionServiceConfig config) {
    return new MongoSessionService(mongoClientSupport);
  }

  @Override
  public String type() {
    return SessionServiceType.MONGODB.name().toLowerCase();
  }
}
