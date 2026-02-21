package com.agentengine.engine.builders.state;

import com.agentengine.engine.api.beans.config.MongoSessionServiceConfig;
import com.agentengine.engine.api.beans.config.SessionServiceConfig.SessionServiceType;
import com.agentengine.engine.api.builders.SessionServiceBuilder;
import com.agentengine.engine.repository.AgentSessionRepository;
import jakarta.inject.Singleton;

@Singleton
public class MongoSessionServiceBuilder
    implements SessionServiceBuilder<MongoSessionServiceConfig, AgentSessionRepository> {
  private final AgentSessionRepository sessionRepository;

  public MongoSessionServiceBuilder(final AgentSessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  @Override
  public AgentSessionRepository build(final MongoSessionServiceConfig config) {
    return sessionRepository;
  }

  @Override
  public String type() {
    return SessionServiceType.MONGODB.name().toLowerCase();
  }
}
